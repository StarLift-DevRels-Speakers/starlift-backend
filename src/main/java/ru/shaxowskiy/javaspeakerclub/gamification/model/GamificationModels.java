package ru.shaxowskiy.javaspeakerclub.gamification.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Модели запросов и ответов соревновательного режима.
 */
public final class GamificationModels {

    private GamificationModels() {
    }

    public enum SeasonStatus {
        ACTIVE,
        CLOSED
    }

    public enum SeasonOutcome {
        PROMOTED,
        STAYED,
        DEMOTED
    }

    public enum LeaderboardPeriod {
        WEEK,
        ALL_TIME
    }

    public record SeasonResponse(
            Long id,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            SeasonStatus status,
            LocalDateTime closedAt
    ) {
    }

    public record BadgeDefinitionResponse(
            BadgeCode code,
            String title,
            String description,
            BadgeCode.Category category,
            boolean repeatable
    ) {
        public static BadgeDefinitionResponse of(BadgeCode code) {
            return new BadgeDefinitionResponse(code, code.title(), code.description(), code.category(), code.repeatable());
        }
    }

    public record EarnedBadgeResponse(
            BadgeCode code,
            String title,
            String description,
            BadgeCode.Category category,
            Long seasonId,
            LocalDateTime awardedDate
    ) {
    }

    public record GamificationProfileResponse(
            Long speakerId,
            String username,
            String firstName,
            String lastName,
            String avatarColor,
            League league,
            int weeklyPoints,
            int weeklyRank,
            int leagueSize,
            LeagueZone zone,
            int totalPoints,
            int currentStreak,
            int longestStreak,
            boolean checkedInToday,
            SeasonResponse season,
            List<EarnedBadgeResponse> badges
    ) {
    }

    public record CheckInResponse(
            boolean alreadyCheckedIn,
            int awardedPoints,
            int currentStreak,
            int longestStreak,
            List<EarnedBadgeResponse> newBadges
    ) {
    }

    public record PointTransactionResponse(
            UUID id,
            Long speakerId,
            int points,
            PointReason reason,
            String sourceId,
            String comment,
            Long awardedBy,
            LocalDateTime createdDate
    ) {
    }

    public record ManualPointsRequest(
            Integer points,
            String comment
    ) {
    }

    public record LeagueResponse(
            League code,
            String title,
            int order,
            int participants,
            int promotionCount,
            int demotionCount
    ) {
    }

    public record LeaderboardEntry(
            int rank,
            Long speakerId,
            String username,
            String firstName,
            String lastName,
            String avatarColor,
            League league,
            int points,
            LeagueZone zone,
            boolean me
    ) {
    }

    public record LeagueLeaderboardResponse(
            League league,
            SeasonResponse season,
            int promotionCount,
            int demotionCount,
            List<LeaderboardEntry> entries
    ) {
    }

    public record GlobalLeaderboardResponse(
            LeaderboardPeriod period,
            SeasonResponse season,
            List<LeaderboardEntry> entries
    ) {
    }

    public record SeasonResultResponse(
            Long seasonId,
            Long speakerId,
            String username,
            String firstName,
            String lastName,
            League league,
            int rank,
            int points,
            SeasonOutcome outcome,
            League newLeague
    ) {
    }

    public record SeasonCloseResponse(
            SeasonResponse closedSeason,
            SeasonResponse nextSeason,
            int participants,
            int promoted,
            int demoted
    ) {
    }
}
