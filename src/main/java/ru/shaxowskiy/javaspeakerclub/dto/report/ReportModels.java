package ru.shaxowskiy.javaspeakerclub.dto.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Сгруппированные модели для отчётных эндпоинтов.
 */
public final class ReportModels {

    private ReportModels() {
    }

    public enum DashboardPeriod {
        @JsonProperty("30d")
        THIRTY_DAYS("30d"),
        @JsonProperty("quarter")
        QUARTER("quarter"),
        @JsonProperty("year")
        YEAR("year"),
        @JsonProperty("all")
        ALL("all");

        private final String value;

        DashboardPeriod(String value) {
            this.value = value;
        }

        @JsonValue
        public String value() {
            return value;
        }
    }

    public enum DashboardInfluence {
        local, regional, federal, international
    }

    public enum ParsingStatus {
        @JsonProperty("new")
        NEW,
        duplicate,
        confirmed,
        rejected
    }

    public record SelectOption(String label, String value) {
    }

    public record RoleFilterOption(String label, String key) {
    }

    public record RoleFilterGroup(String label, String key, List<RoleFilterOption> children) {
    }

    public record ReportFiltersResponse(
            List<SelectOption> periods,
            List<String> regions,
            List<String> companies,
            List<RoleFilterGroup> roles,
            List<SelectOption> influences,
            List<SelectOption> tagWindows,
            List<SelectOption> parsingSources,
            List<SelectOption> parsingStatuses
    ) {
    }

    public record TrendSeries(
            List<String> labels,
            List<Integer> talks,
            List<Integer> nps
    ) {
    }

    public record InfluenceBreakdown(
            int local,
            int regional,
            int federal,
            int international
    ) {
    }

    public record DashboardSeriesByPeriod(
            @JsonProperty("30d") TrendSeries last30d,
            TrendSeries quarter,
            TrendSeries year,
            TrendSeries all
    ) {
    }

    public record TagCloudItem(
            String label,
            int weight
    ) {
    }

    public record Achievement(
            String id,
            String title,
            String description,
            int current,
            int target
    ) {
    }

    public record RecommendationCard(
            String id,
            String title,
            String city,
            DashboardInfluence level,
            String topic,
            LocalDate deadline,
            int match,
            String hostCompany,
            String url
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DashboardSpeaker(
            Long id,
            String name,
            String username,
            String role,
            String roleKey,
            String roleGroup,
            String roleGroupKey,
            String region,
            String company,
            DashboardInfluence influence,
            Integer nps,
            Integer previousNps,
            Integer talksCount,
            LocalDateTime lastTalkDate,
            Boolean topConferenceCandidate,
            List<String> tags,
            Integer profileCompletion,
            LocalDateTime lastEvaluationDate,
            Integer audience,
            InfluenceBreakdown talkMix,
            DashboardSeriesByPeriod series,
            List<Integer> heatmap,
            List<Achievement> achievements,
            List<RecommendationCard> recommendations,
            String avatarColor
    ) {
    }

    public record SpeakerDashboardSummary(
            Integer talksCount,
            LocalDateTime lastTalkDate,
            Integer nps,
            Integer previousNps,
            String trendDirection,
            Integer trendDelta,
            Integer audience,
            Integer activityGrowth,
            Integer activeTopicsCount,
            Integer relevantRecommendationsCount
    ) {
    }

    public record SpeakerDashboardReportRequest(
            DashboardPeriod period,
            List<String> targetRegions,
            List<String> targetRoles,
            List<String> targetCompanies,
            DashboardInfluence targetInfluence,
            Integer npsMin,
            Integer npsMax
    ) {
    }

    public record SpeakerDashboardReport(
            LocalDateTime generatedAt,
            SpeakerDashboardReportRequest filtersApplied,
            DashboardSpeaker speaker,
            TrendSeries currentSeries,
            SpeakerDashboardSummary summary,
            List<TagCloudItem> tagCloud,
            List<RecommendationCard> filteredRecommendations
    ) {
    }

    public record DevRelDashboardReportRequest(
            DashboardPeriod period,
            List<String> regions,
            List<String> roles,
            List<String> companies,
            DashboardInfluence influence,
            Integer npsMin,
            Integer npsMax,
            String speakerSearch,
            String tagWindow,
            String parsingSource,
            ParsingStatus parsingStatus
    ) {
    }

    public record DevRelDashboardSummary(
            Integer activeSpeakers,
            Integer avgNps,
            Integer risingSpeakers,
            Integer confirmedRate,
            Integer topConferenceCandidates
    ) {
    }

    public record DevRelRatingSection(
            List<DashboardSpeaker> speakers,
            List<DashboardSpeaker> topConferenceCandidates,
            Integer averageTalks,
            Integer internationalSpeakers
    ) {
    }

    public record DevRelAnalyticsSection(
            TrendSeries aggregateSeries,
            InfluenceBreakdown influenceMix,
            List<TagCloudItem> tagCloud,
            String dominantTag
    ) {
    }

    public record BlindSpotRow(
            DashboardSpeaker speaker,
            List<String> reasons,
            Integer daysWithoutReview
    ) {
    }

    public record DevRelBlindSpotsSection(
            Integer unevaluatedShare,
            Integer inactiveSpeakersCount,
            Integer incompleteProfilesCount,
            List<BlindSpotRow> rows
    ) {
    }

    public record ParsingSourceBucket(
            String source,
            Integer count
    ) {
    }

    public record ParsingEvent(
            String id,
            String conference,
            Long speakerId,
            String speakerName,
            String source,
            ParsingStatus status,
            String region,
            LocalDateTime date,
            String note
    ) {
    }

    public record DevRelParsingSection(
            Integer newParsingEvents,
            Integer duplicateParsingEvents,
            Integer parsingConfirmedRate,
            List<ParsingSourceBucket> eventsBySource,
            List<ParsingEvent> events
    ) {
    }

    public record DevRelDashboardReport(
            LocalDateTime generatedAt,
            DevRelDashboardReportRequest filtersApplied,
            DevRelDashboardSummary summary,
            DevRelRatingSection rating,
            DevRelAnalyticsSection analytics,
            DevRelBlindSpotsSection blindSpots,
            DevRelParsingSection parsing
    ) {
    }
}
