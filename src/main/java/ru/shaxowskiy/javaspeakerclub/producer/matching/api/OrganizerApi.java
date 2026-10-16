package ru.shaxowskiy.javaspeakerclub.producer.matching.api;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Запросы и ответы сценария «Мероприятие → Спикер» (US-01 — US-09).
 */
public final class OrganizerApi {

    private OrganizerApi() {
    }

    /**
     * Мероприятие из текста и/или полей формы. params — ключи параметров карточки (topics, audienceLevel, city …),
     * criteria — требования, обязательность которых задал сам организатор.
     */
    public record EventCreateRequest(
            String title,
            String description,
            Map<String, List<String>> params,
            List<String> tags,
            List<CriterionRequest> criteria
    ) {
    }

    /** kind: mandatory или desirable; check: language, city, attendance, competency, industry, topic, format, audience, other. */
    public record CriterionRequest(String text, String kind, String check, String value) {
    }

    /**
     * Изменение карточки (US-02, US-07). null — поле не меняется. params с пустым списком очищает параметр,
     * answers — ответы на уточняющие вопросы, skip — пропущенные вопросы, removeCriteria — id требований.
     */
    public record EventUpdateRequest(
            String title,
            String description,
            Map<String, List<String>> params,
            Map<String, String> answers,
            List<String> skip,
            List<CriterionRequest> addCriteria,
            List<String> removeCriteria,
            List<String> tags
    ) {
    }

    /** action: shortlisted, invited, marked_irrelevant или talk_given. */
    public record CandidateActionRequest(String action, String note) {
    }

    /** Открыть CFP мероприятия: оно появится в подборе мероприятий у спикеров. level: local, regional, federal, international. */
    public record CfpPublishRequest(LocalDate deadline, String url, String level) {
    }

    public record CfpPublishResponse(String cfpId, String title, LocalDate deadline, List<String> requirements, List<String> tags) {
    }

    /** Карточка в списке мероприятий организатора. */
    public record EventListItem(String id, String title, String status, int openQuestions, LocalDateTime updatedDate) {
    }

    /** Запись истории: kind = fact — действие человека, kind = ai — рекомендация ИИ. */
    public record HistoryItem(
            String label,
            String expertName,
            String kind,
            String action,
            String confidence,
            String note,
            LocalDateTime date,
            Long expertId
    ) {
    }

    /** Результат действия с кандидатом и текущий статус приглашения. */
    public record CandidateActionResponse(
            String expertName,
            String action,
            String label,
            String invitationStatus,
            List<String> alsoRecorded,
            LocalDateTime date
    ) {
    }
}
