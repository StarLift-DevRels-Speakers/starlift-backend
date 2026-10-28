package ru.shaxowskiy.javaspeakerclub.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.ApplicationCreateRequest;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.ApplicationResponse;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.TalkChange;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventApplicationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerTalksRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.notification.NotificationService;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerTalkRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Заявки спикеров на CFP: подача, смена доклада и решения DevRel-владельца мероприятия.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    static final String STATUS_SENT = "sent";
    static final String STATUS_ACCEPTED = "accepted";
    static final String STATUS_RESERVE = "reserve";
    static final String STATUS_REJECTED = "rejected";
    private static final Map<String, String> DECISION_LABELS = Map.of(
            STATUS_ACCEPTED, "принята",
            STATUS_RESERVE, "резерв",
            STATUS_REJECTED, "отклонена"
    );

    private final EventRepository eventRepository;
    private final SpeakerTalkRepository speakerTalkRepository;
    private final UserRepository userRepository;
    private final SpeakerAccessGuard accessGuard;
    private final NotificationService notificationService;

    /**
     * DevRel видит заявки на свои мероприятия, спикер — свои заявки.
     */
    @Transactional(readOnly = true)
    public List<ApplicationResponse> findVisible() {
        Long me = accessGuard.currentUserId();
        List<EventApplicationsRecord> applications = accessGuard.isDevrel()
                ? eventRepository.findApplicationsForOwner(me)
                : eventRepository.findApplicationsBySpeaker(me);
        Map<Long, UsersRecord> speakers = userRepository.findByIds(applications.stream().map(EventApplicationsRecord::getSpeakerId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UsersRecord::getId, Function.identity()));
        return applications.stream()
                .map(application -> toResponse(application, speakers.get(application.getSpeakerId())))
                .toList();
    }

    @Transactional
    public void submit(ApplicationCreateRequest request) {
        UsersRecord me = accessGuard.currentUser();
        if (request == null || request.eventId() == null || request.talkId() == null) {
            throw EventRules.badRequest("Выберите мероприятие и доклад");
        }
        EventsRecord event = eventRepository.findEvent(request.eventId())
                .filter(item -> EventService.STATE_PUBLISHED.equals(item.getState()))
                .orElseThrow(() -> EventRules.notFound("Недоступное мероприятие или доклад"));
        SpeakerTalksRecord talk = speakerTalkRepository.findById(request.talkId())
                .filter(item -> item.getSpeakerId().equals(me.getId()))
                .orElseThrow(() -> EventRules.notFound("Недоступное мероприятие или доклад"));

        if (!EventRules.isCfpOpen(event, LocalDate.now())) {
            throw EventRules.conflict("Прием заявок завершен");
        }
        if (eventRepository.isSpeakerConfirmed(event.getId(), me.getId())) {
            throw EventRules.conflict("Вы уже участвуете в этом мероприятии");
        }
        if (eventRepository.hasPendingInvitation(event.getId(), me.getId())) {
            throw EventRules.conflict("У вас есть приглашение на это мероприятие — ответьте на него");
        }
        if (talksInUse(eventRepository.findApplications(event.getId(), me.getId()), null).contains(talk.getId())) {
            throw EventRules.conflict("Заявка с этим докладом уже отправлена");
        }

        eventRepository.insertApplication(event.getId(), me.getId(), talk.getId(), talk.getName());
        notificationService.notify(event.getOwnerId(), "Поступила заявка на " + event.getName());
    }

    @Transactional
    public void requestTalkChange(UUID applicationId, UUID talkId) {
        Long me = accessGuard.currentUserId();
        EventApplicationsRecord application = eventRepository.findApplication(applicationId)
                .filter(item -> item.getSpeakerId().equals(me))
                .orElseThrow(() -> EventRules.notFound("Заявка не найдена"));
        EventsRecord event = eventRepository.findEvent(application.getEventId()).orElseThrow();
        if (application.getChangeTopic() != null) {
            throw EventRules.conflict("Запрос на смену доклада уже ожидает ответа DevRel");
        }
        if (!canRequestTalkChange(application, event)) {
            throw EventRules.conflict("Сменить доклад можно в заявке на рассмотрении (пока открыт CFP) или в принятой заявке");
        }
        SpeakerTalksRecord talk = talkId == null ? null : speakerTalkRepository.findById(talkId)
                .filter(item -> item.getSpeakerId().equals(me))
                .orElse(null);
        if (talk == null) {
            throw EventRules.notFound("Доклад не найден");
        }
        if (talk.getId().equals(application.getSpeakerTalkId())) {
            throw EventRules.badRequest("Выберите другой доклад");
        }
        if (talksInUse(eventRepository.findApplications(event.getId(), me), application.getId()).contains(talk.getId())) {
            throw EventRules.conflict("Заявка с этим докладом уже отправлена");
        }

        eventRepository.setTalkChange(applicationId, talk.getId(), talk.getName());
        notificationService.notify(event.getOwnerId(), "Запрошена смена доклада в заявке на «%s»: «%s» → «%s»"
                .formatted(event.getName(), application.getTopic(), talk.getName()));
    }

    @Transactional
    public void answerTalkChange(UUID applicationId, boolean approved) {
        EventApplicationsRecord application = requireOwnedApplication(applicationId);
        EventsRecord event = eventRepository.findEvent(application.getEventId()).orElseThrow();
        if (application.getChangeTopic() == null) {
            throw EventRules.conflict("Запроса на смену доклада нет");
        }
        // Отклонить можно всегда; для подтверждения доклад должен существовать и не дублироваться в заявках.
        if (approved) {
            if (EventRules.isPast(event, LocalDate.now())) {
                throw EventRules.conflict("Мероприятие уже прошло");
            }
            if (application.getChangeTalkId() == null) {
                throw EventRules.conflict("Доклад больше не существует");
            }
            List<EventApplicationsRecord> speakerApplications = eventRepository.findApplications(event.getId(), application.getSpeakerId());
            if (talksInUse(speakerApplications, application.getId()).contains(application.getChangeTalkId())) {
                throw EventRules.conflict("С этим докладом уже есть другая заявка");
            }
            eventRepository.applyTalkChange(applicationId, application.getChangeTalkId(), application.getChangeTopic());
        } else {
            eventRepository.setTalkChange(applicationId, null, null);
        }
        notificationService.notify(application.getSpeakerId(), "Смена доклада в заявке на «%s»: %s"
                .formatted(event.getName(), approved ? "подтверждена" : "отклонена"));
    }

    @Transactional
    public void decide(UUID applicationId, String status) {
        if (!DECISION_LABELS.containsKey(status)) {
            throw EventRules.badRequest("Решение: accepted, reserve или rejected");
        }
        EventApplicationsRecord application = requireOwnedApplication(applicationId);
        EventsRecord event = eventRepository.findEvent(application.getEventId()).orElseThrow();
        // Заявку в резерве ещё можно принять или отклонить.
        if (!STATUS_SENT.equals(application.getStatus()) && !STATUS_RESERVE.equals(application.getStatus())) {
            throw EventRules.conflict("Решение по заявке уже принято");
        }
        if (application.getStatus().equals(status)) {
            throw EventRules.conflict("Заявка уже в этом статусе");
        }
        if (application.getChangeTopic() != null) {
            throw EventRules.conflict("Сначала ответьте на запрос смены доклада");
        }
        if (EventRules.isPast(event, LocalDate.now())) {
            throw EventRules.conflict("Мероприятие уже прошло");
        }
        if (STATUS_ACCEPTED.equals(status) && eventRepository.isSpeakerConfirmed(event.getId(), application.getSpeakerId())) {
            throw EventRules.conflict("Спикер уже участвует в этом мероприятии");
        }

        eventRepository.setApplicationStatus(applicationId, status);
        notificationService.notify(application.getSpeakerId(), "Заявка на «%s»: %s".formatted(event.getName(), DECISION_LABELS.get(status)));
    }

    /**
     * Смена доклада: в заявке на рассмотрении — пока открыт CFP, в принятой — до дня мероприятия.
     */
    private static boolean canRequestTalkChange(EventApplicationsRecord application, EventsRecord event) {
        LocalDate today = LocalDate.now();
        if (EventRules.isPast(event, today)) {
            return false;
        }
        if (STATUS_SENT.equals(application.getStatus())) {
            return EventRules.isCfpOpen(event, today);
        }
        return STATUS_ACCEPTED.equals(application.getStatus());
    }

    /**
     * Доклады, занятые заявками спикера на мероприятие, включая запрошенные смены.
     */
    private static Set<UUID> talksInUse(List<EventApplicationsRecord> applications, UUID exceptId) {
        Set<UUID> used = new HashSet<>();
        for (EventApplicationsRecord item : applications) {
            if (item.getId().equals(exceptId)) {
                continue;
            }
            if (item.getSpeakerTalkId() != null) {
                used.add(item.getSpeakerTalkId());
            }
            if (item.getChangeTalkId() != null) {
                used.add(item.getChangeTalkId());
            }
        }
        return used;
    }

    private EventApplicationsRecord requireOwnedApplication(UUID applicationId) {
        Long me = accessGuard.currentUserId();
        return eventRepository.findApplication(applicationId)
                .filter(application -> eventRepository.findEvent(application.getEventId())
                        .map(event -> event.getOwnerId().equals(me))
                        .orElse(false))
                .orElseThrow(() -> EventRules.notFound("Заявка не найдена"));
    }

    private static ApplicationResponse toResponse(EventApplicationsRecord record, UsersRecord speaker) {
        return new ApplicationResponse(
                record.getId(),
                record.getEventId(),
                record.getSpeakerId(),
                EventRules.displayName(speaker),
                record.getSpeakerTalkId(),
                record.getTopic(),
                record.getCreatedDate().toLocalDate(),
                record.getStatus(),
                record.getChangeTopic() == null ? null : new TalkChange(record.getChangeTalkId(), record.getChangeTopic())
        );
    }
}
