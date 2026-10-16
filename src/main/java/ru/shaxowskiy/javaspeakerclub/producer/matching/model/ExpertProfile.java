package ru.shaxowskiy.javaspeakerclub.producer.matching.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Профиль эксперта (US-10, US-11): экспертиза по четырём уровням п. 7 ТЗ, публичный опыт, отзывы и пробелы в данных.
 */
public record ExpertProfile(
        Expert expert,
        List<ExpertiseArea> expertise,
        PublicExperience publicExperience,
        FeedbackSummary feedback,
        PlatformHistory history,
        ExpertFacts.Preferences preferences,
        List<Gap> gaps,
        String dataCompleteness
) {

    /** Есть доклад или другое подтверждение. */
    public static final String CONFIRMED = "confirmed";
    /** Есть основания (профиль, описание опыта, должность), но нет подтверждений. */
    public static final String POTENTIAL = "potential";
    /** Близкая область: часто встречается вместе с подтверждёнными темами в докладах платформы. */
    public static final String ADJACENT = "adjacent";
    /** Нельзя подтвердить: указано только как интерес. */
    public static final String INSUFFICIENT_DATA = "insufficient_data";

    public record Expert(long id, String name, String role, String company, String region, String stage) {
    }

    public record ExpertiseArea(String area, String level, List<Evidence> evidence, LocalDate lastConfirmed) {
    }

    /** type: talk, profile_tag, about, role, cooccurrence, interest. */
    public record Evidence(String type, String id, String label, LocalDate date) {
    }

    public record PublicExperience(
            int talksTotal,
            int talksLast12Months,
            LocalDate lastTalkDate,
            Map<String, Integer> byConferenceType,
            Map<String, Integer> byFormat,
            List<ExpertFacts.TalkFact> recentTalks
    ) {
    }

    public record FeedbackSummary(
            int total,
            int fromOrganizers,
            int fromParticipants,
            Double averageRating,
            boolean contradictory,
            String note,
            List<Review> recent
    ) {
    }

    /** Отзыв вместе с источником и контекстом доклада (US-10, US-19): без контекста отзыв не интерпретируется. */
    public record Review(
            String talkTopic,
            String event,
            LocalDate talkDate,
            String format,
            String authorType,
            int rating,
            String comment,
            LocalDate date
    ) {
    }

    /** Факты из истории на платформе. Пометки организаторов («не подходит») эксперту не показываются. */
    public record PlatformHistory(int acceptedInvitations, int talksGiven, int repeatInvitations) {
    }

    /** Существенный пробел: что не указано и на что это влияет. */
    public record Gap(String field, String message) {
    }
}
