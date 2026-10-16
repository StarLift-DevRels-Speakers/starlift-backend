package ru.shaxowskiy.javaspeakerclub.producer.matching.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Мероприятия и CFP для эксперта (US-12, US-13). Требования берутся только из данных CFP:
 * то, чего в источнике нет, перечислено в unknowns, а не додумано.
 */
public record EventRecommendationResult(
        List<Recommendation.Group<Event>> groups,
        List<String> profileGaps,
        List<String> notes,
        int consideredCount,
        long expertId,
        String runId,
        LocalDateTime generatedAt
) {

    /**
     * Объяснение по п. 16 ТЗ. Поля идут в порядке важности для читающего: что за мероприятие, почему подходит,
     * дедлайн, ограничения и уверенность. aiSummary — короткий пересказ от модели, есть только если прошёл проверку.
     * eventId заполнен, если CFP открыл организатор платформы.
     */
    public record Event(
            String title,
            String aiSummary,
            List<String> whyFits,
            Cfp cfp,
            List<String> limitations,
            List<String> unknowns,
            Recommendation.Confidence confidence,
            List<String> requirements,
            List<Recommendation.TalkRef> relevantExperience,
            String description,
            String cfpId,
            String eventId
    ) {

        public Event withAiSummary(String summary) {
            return new Event(title, summary, whyFits, cfp, limitations, unknowns, confidence, requirements,
                    relevantExperience, description, cfpId, eventId);
        }
    }

    public record Cfp(boolean present, LocalDate deadline, long daysLeft, String url) {
    }
}
