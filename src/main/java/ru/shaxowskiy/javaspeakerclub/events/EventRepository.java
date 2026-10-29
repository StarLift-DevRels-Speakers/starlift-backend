package ru.shaxowskiy.javaspeakerclub.events;

import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.EventApplications;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.EventInvitations;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Events;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventApplicationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventInvitationsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Доступ к мероприятиям, заявкам и приглашениям.
 */
@Repository
@RequiredArgsConstructor
public class EventRepository {

    private static final Events E = Events.EVENTS;
    private static final EventApplications A = EventApplications.EVENT_APPLICATIONS;
    private static final EventInvitations I = EventInvitations.EVENT_INVITATIONS;

    private final DSLContext dsl;

    // ---------- Events ----------

    public Optional<EventsRecord> findEvent(UUID id) {
        return dsl.selectFrom(E).where(E.ID.eq(id)).fetchOptional();
    }

    /**
     * Опубликованные мероприятия и все мероприятия владельца (ownerId может быть null).
     */
    public List<EventsRecord> findVisible(Long ownerId) {
        var condition = E.STATE.eq(EventService.STATE_PUBLISHED);
        if (ownerId != null) {
            condition = condition.or(E.OWNER_ID.eq(ownerId));
        }
        return dsl.selectFrom(E).where(condition).orderBy(E.EVENT_DATE.asc()).fetch();
    }

    public List<EventsRecord> findByIds(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return dsl.selectFrom(E).where(E.ID.in(ids)).fetch();
    }

    public EventsRecord insertEvent(Long ownerId, EventModels.EventRequest input) {
        return dsl.insertInto(E)
                .set(E.ID, UUID.randomUUID())
                .set(E.OWNER_ID, ownerId)
                .set(E.NAME, input.name())
                .set(E.EVENT_DATE, input.date())
                .set(E.CFP_DEADLINE, input.cfp())
                .set(E.CITY, input.city())
                .set(E.FORMAT, input.format())
                .set(E.TOPIC, input.topic())
                .set(E.AUDIENCE, input.audience())
                .set(E.DESCRIPTION, input.description())
                .set(E.STATE, EventService.STATE_DRAFT)
                .set(E.CREATED_DATE, LocalDateTime.now())
                .returning()
                .fetchOne();
    }

    public void updateEvent(UUID id, EventModels.EventRequest input) {
        dsl.update(E)
                .set(E.NAME, input.name())
                .set(E.EVENT_DATE, input.date())
                .set(E.CFP_DEADLINE, input.cfp())
                .set(E.CITY, input.city())
                .set(E.FORMAT, input.format())
                .set(E.TOPIC, input.topic())
                .set(E.AUDIENCE, input.audience())
                .set(E.DESCRIPTION, input.description())
                .where(E.ID.eq(id))
                .execute();
    }

    public void setEventState(UUID id, String state) {
        dsl.update(E).set(E.STATE, state).where(E.ID.eq(id)).execute();
    }

    // ---------- Applications ----------

    public Optional<EventApplicationsRecord> findApplication(UUID id) {
        return dsl.selectFrom(A).where(A.ID.eq(id)).fetchOptional();
    }

    public List<EventApplicationsRecord> findApplicationsBySpeaker(Long speakerId) {
        return dsl.selectFrom(A).where(A.SPEAKER_ID.eq(speakerId)).orderBy(A.CREATED_DATE.desc()).fetch();
    }

    public List<EventApplicationsRecord> findApplicationsForOwner(Long ownerId) {
        return dsl.select(A.fields())
                .from(A)
                .join(E).on(E.ID.eq(A.EVENT_ID))
                .where(E.OWNER_ID.eq(ownerId))
                .orderBy(A.CREATED_DATE.desc())
                .fetchInto(EventApplicationsRecord.class);
    }

    public List<EventApplicationsRecord> findApplications(UUID eventId, Long speakerId) {
        return dsl.selectFrom(A).where(A.EVENT_ID.eq(eventId)).and(A.SPEAKER_ID.eq(speakerId)).fetch();
    }

    public void insertApplication(UUID eventId, Long speakerId, UUID talkId, String topic) {
        dsl.insertInto(A)
                .set(A.ID, UUID.randomUUID())
                .set(A.EVENT_ID, eventId)
                .set(A.SPEAKER_ID, speakerId)
                .set(A.SPEAKER_TALK_ID, talkId)
                .set(A.TOPIC, topic)
                .set(A.STATUS, ApplicationService.STATUS_SENT)
                .set(A.CREATED_DATE, LocalDateTime.now())
                .execute();
    }

    public void setApplicationStatus(UUID id, String status) {
        dsl.update(A).set(A.STATUS, status).where(A.ID.eq(id)).execute();
    }

    public void setTalkChange(UUID id, UUID talkId, String topic) {
        dsl.update(A).set(A.CHANGE_TALK_ID, talkId).set(A.CHANGE_TOPIC, topic).where(A.ID.eq(id)).execute();
    }

    public void applyTalkChange(UUID id, UUID talkId, String topic) {
        dsl.update(A)
                .set(A.SPEAKER_TALK_ID, talkId)
                .set(A.TOPIC, topic)
                .setNull(A.CHANGE_TALK_ID)
                .setNull(A.CHANGE_TOPIC)
                .where(A.ID.eq(id))
                .execute();
    }

    // ---------- Invitations ----------

    public Optional<EventInvitationsRecord> findInvitation(UUID id) {
        return dsl.selectFrom(I).where(I.ID.eq(id)).fetchOptional();
    }

    public List<EventInvitationsRecord> findInvitationsBySpeaker(Long speakerId) {
        return dsl.selectFrom(I).where(I.SPEAKER_ID.eq(speakerId)).orderBy(I.CREATED_DATE.desc()).fetch();
    }

    public List<EventInvitationsRecord> findInvitationsForOwner(Long ownerId) {
        return dsl.select(I.fields())
                .from(I)
                .join(E).on(E.ID.eq(I.EVENT_ID))
                .where(E.OWNER_ID.eq(ownerId))
                .orderBy(I.CREATED_DATE.desc())
                .fetchInto(EventInvitationsRecord.class);
    }

    public List<EventInvitationsRecord> findInvitations(UUID eventId, Long speakerId) {
        return dsl.selectFrom(I).where(I.EVENT_ID.eq(eventId)).and(I.SPEAKER_ID.eq(speakerId)).fetch();
    }

    public void insertInvitation(UUID eventId, Long speakerId, String topic) {
        dsl.insertInto(I)
                .set(I.ID, UUID.randomUUID())
                .set(I.EVENT_ID, eventId)
                .set(I.SPEAKER_ID, speakerId)
                .set(I.TOPIC, topic)
                .set(I.STATUS, InvitationService.STATUS_SENT)
                .set(I.CREATED_DATE, LocalDateTime.now())
                .execute();
    }

    public void setInvitationStatus(UUID id, String status) {
        dsl.update(I).set(I.STATUS, status).where(I.ID.eq(id)).execute();
    }

    // ---------- Participation ----------

    /**
     * Спикер уже участвует: заявка принята или приглашение подтверждено.
     */
    public boolean isSpeakerConfirmed(UUID eventId, Long speakerId) {
        return dsl.fetchExists(dsl.selectFrom(A)
                .where(A.EVENT_ID.eq(eventId)).and(A.SPEAKER_ID.eq(speakerId)).and(A.STATUS.eq(ApplicationService.STATUS_ACCEPTED)))
                || dsl.fetchExists(dsl.selectFrom(I)
                .where(I.EVENT_ID.eq(eventId)).and(I.SPEAKER_ID.eq(speakerId)).and(I.STATUS.eq(InvitationService.STATUS_ACCEPTED)));
    }

    public boolean hasPendingInvitation(UUID eventId, Long speakerId) {
        return dsl.fetchExists(dsl.selectFrom(I)
                .where(I.EVENT_ID.eq(eventId)).and(I.SPEAKER_ID.eq(speakerId)).and(I.STATUS.eq(InvitationService.STATUS_SENT)));
    }
}
