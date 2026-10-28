package ru.shaxowskiy.javaspeakerclub.events;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.EventRequest;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.EventResponse;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.notification.NotificationService;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Мероприятия DevRel: черновик → публикация. Спикер видит только опубликованные.
 * Платный доступ к публикации проверяет фронтенд (подписки пока демо).
 */
@Service
@RequiredArgsConstructor
public class EventService {

    static final String STATE_DRAFT = "draft";
    static final String STATE_PUBLISHED = "published";
    private static final Set<String> FORMATS = Set.of("OFFLINE", "ONLINE", "HYBRID");

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final SpeakerAccessGuard accessGuard;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<EventResponse> findVisible() {
        UsersRecord me = accessGuard.currentUser();
        List<EventsRecord> events = eventRepository.findVisible(accessGuard.isDevrel() ? me.getId() : null);
        Map<Long, UsersRecord> owners = userRepository.findByIds(events.stream().map(EventsRecord::getOwnerId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UsersRecord::getId, Function.identity()));
        return events.stream()
                .map(event -> toResponse(event, owners.get(event.getOwnerId()), me.getId()))
                .toList();
    }

    @Transactional
    public EventResponse create(EventRequest request) {
        EventRequest input = validate(request, null);
        UsersRecord me = accessGuard.currentUser();
        return toResponse(eventRepository.insertEvent(me.getId(), input), me, me.getId());
    }

    @Transactional
    public EventResponse update(UUID id, EventRequest request) {
        UsersRecord me = accessGuard.currentUser();
        EventsRecord existing = eventRepository.findEvent(id)
                .filter(event -> event.getOwnerId().equals(me.getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к чужому мероприятию"));
        eventRepository.updateEvent(id, validate(request, existing));
        return toResponse(eventRepository.findEvent(id).orElseThrow(), me, me.getId());
    }

    @Transactional
    public EventResponse publish(UUID id) {
        UsersRecord me = accessGuard.currentUser();
        EventsRecord event = eventRepository.findEvent(id)
                .filter(item -> item.getOwnerId().equals(me.getId()))
                .orElseThrow(() -> EventRules.notFound("Нет доступа к мероприятию"));
        if (STATE_PUBLISHED.equals(event.getState())) {
            throw EventRules.conflict("Мероприятие уже опубликовано");
        }
        eventRepository.setEventState(id, STATE_PUBLISHED);
        notificationService.notify(me.getId(), "Мероприятие опубликовано: " + event.getName());
        return toResponse(eventRepository.findEvent(id).orElseThrow(), me, me.getId());
    }

    /**
     * Прошедшую дату, которую не меняли, можно оставить: прошедшее мероприятие остаётся редактируемым.
     */
    private static EventRequest validate(EventRequest request, EventsRecord existing) {
        if (request == null || isBlank(request.name()) || isBlank(request.topic()) || request.date() == null) {
            throw EventRules.badRequest("Заполните обязательные поля");
        }
        String format = request.format() == null ? "" : request.format().trim().toUpperCase();
        if (!FORMATS.contains(format)) {
            throw EventRules.badRequest("Укажите формат: OFFLINE, ONLINE или HYBRID");
        }
        if (request.cfp() != null && request.cfp().isAfter(request.date())) {
            throw EventRules.badRequest("Дедлайн CFP не может быть позже даты мероприятия");
        }
        LocalDate today = LocalDate.now();
        if (request.date().isBefore(today) && (existing == null || !request.date().equals(existing.getEventDate()))) {
            throw EventRules.badRequest("Дата мероприятия не может быть в прошлом");
        }
        if (request.cfp() != null && request.cfp().isBefore(today)
                && (existing == null || !Objects.equals(request.cfp(), existing.getCfpDeadline()))) {
            throw EventRules.badRequest("Дедлайн CFP не может быть в прошлом");
        }
        return new EventRequest(
                request.name().trim(),
                request.date(),
                request.cfp(),
                trim(request.city()),
                format,
                request.topic().trim(),
                trim(request.audience()),
                trim(request.description())
        );
    }

    static EventResponse toResponse(EventsRecord event, UsersRecord owner, Long currentUserId) {
        return new EventResponse(
                event.getId(),
                event.getOwnerId(),
                EventRules.displayName(owner),
                event.getOwnerId().equals(currentUserId),
                event.getName(),
                event.getEventDate(),
                event.getCfpDeadline(),
                event.getCity(),
                event.getFormat(),
                event.getTopic(),
                event.getAudience(),
                event.getDescription(),
                event.getState()
        );
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
