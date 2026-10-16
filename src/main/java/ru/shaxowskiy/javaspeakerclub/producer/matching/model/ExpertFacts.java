package ru.shaxowskiy.javaspeakerclub.producer.matching.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Всё, что платформа знает об эксперте: профиль, доклады, отзывы в контексте доклада, предпочтения и история
 * взаимодействий. Единственный источник для профиля эксперта и подбора — вывод AI сюда не попадает.
 */
public record ExpertFacts(
        long id,
        String username,
        String name,
        String company,
        String companyRole,
        String region,
        String stage,
        String about,
        List<String> profileTags,
        Preferences preferences,
        List<TalkFact> talks,
        List<FeedbackFact> feedback,
        Signals signals
) {

    public record TalkFact(
            String id,
            String topic,
            LocalDate date,
            String event,
            String conferenceType,
            String format,
            List<String> tags
    ) {
    }

    /** Отзыв всегда в контексте доклада: тема, формат, мероприятие и дата берутся из talkId. */
    public record FeedbackFact(
            String id,
            String talkId,
            int rating,
            String positive,
            String negative,
            String improvement,
            String authorType,
            LocalDate date
    ) {
    }

    /** Предпочтения эксперта (п. 6.4 ТЗ). null и пустой список — «не указано», а не «нет». */
    public record Preferences(
            List<String> languages,
            List<String> formats,
            Boolean onlineOk,
            Boolean offlineOk,
            List<String> interests,
            String limitations
    ) {
        public static Preferences empty() {
            return new Preferences(List.of(), List.of(), null, null, List.of(), null);
        }
    }

    /**
     * Факты из истории взаимодействий. Отказ и отклонённая заявка не считаются негативом (п. 24 ТЗ),
     * поэтому здесь только положительные сигналы и пометки организатора.
     */
    public record Signals(
            int acceptedInvitations,
            int talksGiven,
            int repeatInvitations,
            Set<String> irrelevantForEvents,
            Map<String, CfpHistory> cfpHistory
    ) {
        public static Signals empty() {
            return new Signals(0, 0, 0, Set.of(), Map.of());
        }

        public int positive() {
            return acceptedInvitations + talksGiven + repeatInvitations;
        }
    }

    /** Последнее действие эксперта по CFP: applied, application_accepted, application_rejected и т. д. */
    public record CfpHistory(String action, LocalDate date) {
    }
}
