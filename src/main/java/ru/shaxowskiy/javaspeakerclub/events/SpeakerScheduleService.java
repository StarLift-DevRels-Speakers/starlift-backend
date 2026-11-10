package ru.shaxowskiy.javaspeakerclub.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.ScheduleItemResponse;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventApplicationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventInvitationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Предстоящие выступления спикера: напоминания на платформе и выгрузка в календарь.
 * Выступление появляется, когда DevRel принял заявку на CFP или спикер принял приглашение.
 */
@Service
@RequiredArgsConstructor
public class SpeakerScheduleService {

    static final String REMINDER_WEEK = "WEEK";
    static final String REMINDER_THREE_DAYS = "THREE_DAYS";
    static final String REMINDER_ONE_DAY = "ONE_DAY";
    static final String REMINDER_TODAY = "TODAY";

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final SpeakerAccessGuard accessGuard;

    /**
     * Предстоящие выступления текущего пользователя, ближайшие первыми; прошедшие и снятые с публикации не входят.
     */
    @Transactional(readOnly = true)
    public List<ScheduleItemResponse> findMine() {
        Long me = accessGuard.currentUserId();
        LocalDate today = LocalDate.now();

        // Тема выступления: из принятой заявки, иначе из подтверждённого приглашения.
        Map<UUID, String> topics = new LinkedHashMap<>();
        for (EventApplicationsRecord application : eventRepository.findApplicationsBySpeaker(me)) {
            if (ApplicationService.STATUS_ACCEPTED.equals(application.getStatus())) {
                topics.putIfAbsent(application.getEventId(), application.getTopic());
            }
        }
        for (EventInvitationsRecord invitation : eventRepository.findInvitationsBySpeaker(me)) {
            if (InvitationService.STATUS_ACCEPTED.equals(invitation.getStatus())) {
                topics.putIfAbsent(invitation.getEventId(), invitation.getTopic());
            }
        }

        List<EventsRecord> events = eventRepository.findByIds(List.copyOf(topics.keySet())).stream()
                .filter(event -> EventService.STATE_PUBLISHED.equals(event.getState()))
                .filter(event -> !EventRules.isPast(event, today))
                .sorted(Comparator.comparing(EventsRecord::getEventDate).thenComparing(EventsRecord::getName))
                .toList();
        Map<Long, UsersRecord> owners = userRepository.findByIds(events.stream().map(EventsRecord::getOwnerId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UsersRecord::getId, Function.identity()));
        return events.stream()
                .map(event -> toItem(event, topics.get(event.getId()), owners.get(event.getOwnerId()), today))
                .toList();
    }

    /**
     * Все предстоящие выступления одним файлом iCalendar.
     */
    @Transactional(readOnly = true)
    public String calendar() {
        return CalendarExport.ics(findMine(), CalendarExport.nowUtc());
    }

    /**
     * Одно выступление файлом iCalendar.
     */
    @Transactional(readOnly = true)
    public String calendar(UUID eventId) {
        ScheduleItemResponse item = findMine().stream()
                .filter(candidate -> candidate.eventId().equals(eventId))
                .findFirst()
                .orElseThrow(() -> EventRules.notFound("Выступление не найдено"));
        return CalendarExport.ics(List.of(item), CalendarExport.nowUtc());
    }

    /**
     * Этап напоминания: с недели до выступления баннер становится всё настойчивее.
     */
    static String reminder(long daysLeft) {
        if (daysLeft < 0 || daysLeft > 7) {
            return null;
        }
        if (daysLeft == 0) {
            return REMINDER_TODAY;
        }
        if (daysLeft == 1) {
            return REMINDER_ONE_DAY;
        }
        return daysLeft <= 3 ? REMINDER_THREE_DAYS : REMINDER_WEEK;
    }

    private static ScheduleItemResponse toItem(EventsRecord event, String topic, UsersRecord owner, LocalDate today) {
        long daysLeft = ChronoUnit.DAYS.between(today, event.getEventDate());
        ScheduleItemResponse item = new ScheduleItemResponse(
                event.getId(),
                event.getName(),
                event.getEventDate(),
                event.getCity(),
                event.getFormat(),
                topic,
                EventRules.displayName(owner),
                daysLeft,
                reminder(daysLeft),
                null
        );
        return new ScheduleItemResponse(
                item.eventId(), item.eventName(), item.date(), item.city(), item.format(), item.topic(),
                item.ownerName(), item.daysLeft(), item.reminder(), CalendarExport.googleCalendarUrl(item)
        );
    }
}
