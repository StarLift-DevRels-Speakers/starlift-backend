package ru.shaxowskiy.javaspeakerclub.events;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Мероприятия с CFP, заявки спикеров и приглашения DevRel.
 * format: OFFLINE, ONLINE или HYBRID; state: draft или published.
 */
public final class EventModels {

    private EventModels() {
    }

    public record EventRequest(
            String name,
            LocalDate date,
            LocalDate cfp,
            String city,
            String format,
            String topic,
            String audience,
            String description
    ) {
    }

    /**
     * own — мероприятие текущего пользователя.
     */
    public record EventResponse(
            UUID id,
            Long ownerId,
            String ownerName,
            boolean own,
            String name,
            LocalDate date,
            LocalDate cfp,
            String city,
            String format,
            String topic,
            String audience,
            String description,
            String state
    ) {
    }

    public record ApplicationCreateRequest(UUID eventId, UUID talkId) {
    }

    public record TalkChangeRequest(UUID talkId) {
    }

    public record TalkChangeAnswerRequest(boolean approved) {
    }

    /**
     * status: accepted, reserve или rejected.
     */
    public record DecisionRequest(String status) {
    }

    public record TalkChange(UUID talkId, String topic) {
    }

    /**
     * status: sent, accepted, reserve или rejected. talkChange — запрошенная смена доклада, ждёт решения DevRel.
     */
    public record ApplicationResponse(
            UUID id,
            UUID eventId,
            Long speakerId,
            String speakerName,
            UUID talkId,
            String topic,
            LocalDate at,
            String status,
            TalkChange talkChange
    ) {
    }

    public record InvitationCreateRequest(UUID eventId, Long speakerId, String topic) {
    }

    /**
     * status: accepted или declined.
     */
    public record InvitationAnswerRequest(String status) {
    }

    /**
     * status: sent, accepted или declined.
     */
    public record InvitationResponse(
            UUID id,
            UUID eventId,
            Long speakerId,
            String speakerName,
            String topic,
            LocalDate date,
            String status
    ) {
    }
}
