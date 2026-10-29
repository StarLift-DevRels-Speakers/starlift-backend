package ru.shaxowskiy.javaspeakerclub.producer.model;

import java.time.LocalDate;
import java.util.List;

/**
 * Факты о спикере, посчитанные кодом. Единственный источник данных для LLM и валидатора.
 */
public record SpeakerSnapshot(
        LocalDate generatedOn,
        Speaker speaker,
        Activity activity,
        Nps nps,
        List<TalkItem> talks,
        List<FeedbackItem> feedback,
        FeedbackStats feedbackStats,
        List<CfpItem> cfps,
        List<String> profileGaps
) {

    public record Speaker(
            Long id,
            String username,
            String name,
            String company,
            String companyRole,
            String region,
            List<String> roles,
            String stage,
            String nextStage
    ) {
    }

    /**
     * momentum: growing, stable, declining или inactive — сравнение докладов за последние 90 дней с предыдущими 90.
     */
    public record Activity(
            int talksTotal,
            int talksLast90Days,
            int talksPrevious90Days,
            LocalDate lastTalkDate,
            Integer daysSinceLastTalk,
            String momentum
    ) {
    }

    /**
     * NPS в шкале 0–100, как на дашбордах.
     */
    public record Nps(
            Integer current,
            Integer previous,
            Integer delta
    ) {
    }

    public record TalkItem(
            String id,
            String topic,
            LocalDate date,
            String event,
            String conferenceType,
            String format,
            List<String> tags
    ) {
    }

    /**
     * Отзыв вместе с контекстом доклада (п. 8 ТЗ): тема, формат и мероприятие. authorType — participant, organizer или other.
     */
    public record FeedbackItem(
            String id,
            String talkId,
            String talkTopic,
            String talkFormat,
            String authorType,
            int rating,
            String positive,
            String negative,
            String improvement,
            LocalDate date
    ) {
    }

    public record FeedbackStats(
            int count,
            Double averageRating,
            Integer promotersPercent,
            Integer detractorsPercent,
            Integer feedbackNps
    ) {
    }

    /**
     * Открытый CFP из подбора мероприятий (CfpMatcher): вместо одного числа совпадения — группа, уверенность и причины.
     * group: high, potential или needs_check; confidence: high, medium или low.
     */
    public record CfpItem(
            String id,
            String title,
            String city,
            String level,
            String topic,
            LocalDate deadline,
            String group,
            String confidence,
            List<String> whyFits,
            List<String> tags,
            String url
    ) {
    }
}
