package ru.shaxowskiy.javaspeakerclub.producer.matching.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Результат подбора спикеров под мероприятие (US-04 — US-06): группы по релевантности,
 * объяснение по каждому кандидату и отдельный список исключённых с причинами.
 */
public record SpeakerMatchResult(
        String eventTitle,
        List<Recommendation.Group<Candidate>> groups,
        List<Excluded> excluded,
        List<String> notes,
        List<String> eventSummary,
        int consideredCount,
        String eventId,
        String runId,
        LocalDateTime generatedAt
) {

    /**
     * Объяснение по п. 12 ТЗ: кто, почему подходит, что совпало, ограничения, чего не хватает, уверенность.
     * aiSummary — короткий пересказ от модели, есть только если прошёл проверку по фактам.
     */
    public record Candidate(
            String name,
            String role,
            String aiSummary,
            List<String> whyFits,
            List<String> limitations,
            List<String> missingData,
            Recommendation.Confidence confidence,
            List<String> matchedCriteria,
            List<String> keyExpertise,
            List<Recommendation.TalkRef> relevantTalks,
            String feedbackInfluence,
            String region,
            long expertId
    ) {

        public Candidate withAiSummary(String summary) {
            return new Candidate(name, role, summary, whyFits, limitations, missingData, confidence, matchedCriteria,
                    keyExpertise, relevantTalks, feedbackInfluence, region, expertId);
        }
    }

    /** Кандидат с нарушенным обязательным требованием: исключён, но видно почему (US-04). */
    public record Excluded(String name, List<String> reasons, long expertId) {
    }
}
