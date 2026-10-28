package ru.shaxowskiy.javaspeakerclub.signals.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Тело POST /signals/weak-signals. Контракт задаёт сервис weak-signals (TalentRequest), поэтому имена — snake_case.
 * null означает «в базе нет данных», а не ноль. Исключения: avg_rating без отзывов — 0 (вместе с feedback_count = 0),
 * achievements_progress_pct без целей — временная случайная заглушка (TODO в TalentRequestBuilder).
 */
public record TalentRequest(
        Long id,
        String username,
        String region,
        String company,
        @JsonProperty("company_role") String companyRole,
        String about,
        String influence,

        @JsonProperty("talks_total") int talksTotal,
        @JsonProperty("talks_last_3_months") int talksLast3Months,
        @JsonProperty("last_talk_date") LocalDateTime lastTalkDate,
        @JsonProperty("avg_rating") double avgRating,
        @JsonProperty("feedback_count") int feedbackCount,
        BigDecimal nps,
        @JsonProperty("profile_completion") Integer profileCompletion,
        @JsonProperty("achievements_progress_pct") double achievementsProgressPct,
        @JsonProperty("cfp_high_match_count") int cfpHighMatchCount,

        @JsonProperty("recent_talks") List<TalkItem> recentTalks,
        @JsonProperty("recent_feedback") List<FeedbackItem> recentFeedback,
        List<AchievementItem> achievements,
        @JsonProperty("parsing_events") List<ParsingEventItem> parsingEvents,
        @JsonProperty("profile_tags") List<String> profileTags
) {

    public record TalkItem(
            String topic,
            @JsonProperty("conference_type") String conferenceType,
            String format,
            @JsonProperty("talk_date") LocalDateTime talkDate
    ) {
    }

    public record FeedbackItem(
            int rating,
            String positive,
            String negative,
            @JsonProperty("improvement_suggestions") String improvementSuggestions
    ) {
    }

    public record AchievementItem(
            String title,
            String description,
            @JsonProperty("current_value") int currentValue,
            @JsonProperty("target_value") int targetValue
    ) {
    }

    public record ParsingEventItem(
            String conference,
            String company,
            @JsonProperty("event_date") LocalDate eventDate,
            String note
    ) {
    }
}
