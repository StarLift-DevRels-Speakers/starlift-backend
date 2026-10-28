package ru.shaxowskiy.javaspeakerclub.events;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.InvitationCreateRequest;
import ru.shaxowskiy.javaspeakerclub.events.EventModels.InvitationResponse;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventInvitationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.notification.NotificationService;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRoleRepository;
import ru.shaxowskiy.javaspeakerclub.security.AppRole;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Приглашения DevRel спикеру на своё опубликованное мероприятие.
 */
@Service
@RequiredArgsConstructor
public class InvitationService {

    static final String STATUS_SENT = "sent";
    static final String STATUS_ACCEPTED = "accepted";
    static final String STATUS_DECLINED = "declined";

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final SpeakerAccessGuard accessGuard;
    private final NotificationService notificationService;

    /**
     * DevRel видит приглашения на свои мероприятия, спикер — полученные.
     */
    @Transactional(readOnly = true)
    public List<InvitationResponse> findVisible() {
        Long me = accessGuard.currentUserId();
        List<EventInvitationsRecord> invitations = accessGuard.isDevrel()
                ? eventRepository.findInvitationsForOwner(me)
                : eventRepository.findInvitationsBySpeaker(me);
        Map<Long, UsersRecord> speakers = userRepository.findByIds(invitations.stream().map(EventInvitationsRecord::getSpeakerId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(UsersRecord::getId, Function.identity()));
        return invitations.stream()
                .map(invitation -> new InvitationResponse(
                        invitation.getId(),
                        invitation.getEventId(),
                        invitation.getSpeakerId(),
                        EventRules.displayName(speakers.get(invitation.getSpeakerId())),
                        invitation.getTopic(),
                        invitation.getCreatedDate().toLocalDate(),
                        invitation.getStatus()
                ))
                .toList();
    }

    @Transactional
    public void send(InvitationCreateRequest request) {
        Long me = accessGuard.currentUserId();
        if (request == null || request.eventId() == null || request.speakerId() == null) {
            throw EventRules.badRequest("Выберите мероприятие и спикера");
        }
        EventsRecord event = eventRepository.findEvent(request.eventId())
                .filter(item -> item.getOwnerId().equals(me) && EventService.STATE_PUBLISHED.equals(item.getState()))
                .orElseThrow(() -> EventRules.conflict("Выберите свое опубликованное мероприятие"));
        if (EventRules.isPast(event, LocalDate.now())) {
            throw EventRules.conflict("Мероприятие уже прошло");
        }
        boolean isSpeaker = userRoleRepository.findRolesByUserId(request.speakerId()).contains(AppRole.SPEAKER);
        if (!isSpeaker) {
            throw EventRules.notFound("Спикер не найден");
        }
        if (eventRepository.isSpeakerConfirmed(event.getId(), request.speakerId())) {
            throw EventRules.conflict("Спикер уже участвует в этом мероприятии");
        }
        if (request.topic() == null || request.topic().isBlank()) {
            throw EventRules.badRequest("Укажите предлагаемую тему");
        }
        if (eventRepository.hasPendingInvitation(event.getId(), request.speakerId())) {
            throw EventRules.conflict("Приглашение на это мероприятие уже ожидает ответа Спикера");
        }

        eventRepository.insertInvitation(event.getId(), request.speakerId(), request.topic().trim());
        notificationService.notify(request.speakerId(), "Новое приглашение выступить");
    }

    @Transactional
    public void answer(UUID invitationId, String status) {
        if (!STATUS_ACCEPTED.equals(status) && !STATUS_DECLINED.equals(status)) {
            throw EventRules.badRequest("Ответ: accepted или declined");
        }
        UsersRecord me = accessGuard.currentUser();
        EventInvitationsRecord invitation = eventRepository.findInvitation(invitationId)
                .filter(item -> item.getSpeakerId().equals(me.getId()))
                .orElseThrow(() -> EventRules.notFound("Приглашение не найдено"));
        if (!STATUS_SENT.equals(invitation.getStatus())) {
            throw EventRules.conflict("Ответ на приглашение уже дан");
        }
        EventsRecord event = eventRepository.findEvent(invitation.getEventId()).orElseThrow();
        // Отклонить можно всегда, принять — пока спикер ещё может участвовать.
        if (STATUS_ACCEPTED.equals(status)) {
            if (EventRules.isPast(event, LocalDate.now())) {
                throw EventRules.conflict("Мероприятие уже прошло");
            }
            if (eventRepository.isSpeakerConfirmed(event.getId(), me.getId())) {
                throw EventRules.conflict("Вы уже участвуете в этом мероприятии");
            }
        }

        eventRepository.setInvitationStatus(invitationId, status);
        notificationService.notify(event.getOwnerId(), "%s: приглашение на «%s» %s".formatted(
                EventRules.displayName(me), event.getName(), STATUS_ACCEPTED.equals(status) ? "принято" : "отклонено"));
    }
}
