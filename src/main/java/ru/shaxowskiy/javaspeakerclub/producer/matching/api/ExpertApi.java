package ru.shaxowskiy.javaspeakerclub.producer.matching.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Запросы и ответы сценария «Спикер → Мероприятие» (US-10 — US-13) и ответы на приглашения (US-08, US-09).
 */
public final class ExpertApi {

    private ExpertApi() {
    }

    /** Предпочтения эксперта (п. 6.4 ТЗ). null — не указано. */
    public record PreferencesRequest(
            List<String> languages,
            List<String> formats,
            Boolean onlineOk,
            Boolean offlineOk,
            List<String> interests,
            String limitations
    ) {
    }

    /** action: viewed_event, opened_cfp, applied или marked_irrelevant. */
    public record EventActionRequest(String action, String note) {
    }

    /** response: accepted, declined или needs_clarification. */
    public record InvitationResponseRequest(String response, String note) {
    }

    /**
     * Приглашение глазами спикера: что за мероприятие, почему позвали именно его (из объяснения подбора) и статус.
     */
    public record InvitationView(
            String eventTitle,
            String statusLabel,
            List<String> whyYou,
            String aiSummary,
            List<String> event,
            String organizer,
            String status,
            String note,
            LocalDateTime invitedAt,
            LocalDateTime updatedAt,
            String eventId
    ) {
    }

    public record ActionResponse(String action, String label, LocalDateTime date) {
    }
}
