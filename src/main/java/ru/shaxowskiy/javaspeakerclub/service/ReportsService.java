package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels.*;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ParsingEventsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerAchievementsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerNpsHistoryRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.CfpRecommendationRepository;
import ru.shaxowskiy.javaspeakerclub.repository.ParsingEventRepository;
import ru.shaxowskiy.javaspeakerclub.repository.RoleRepository;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerAchievementRepository;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerNpsHistoryRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportsService {

    private static final InfluenceBreakdown EMPTY_INFLUENCE = new InfluenceBreakdown(0, 0, 0, 0);
    private static final TrendSeries EMPTY_TREND = new TrendSeries(List.of(), List.of(), List.of());
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("ru-RU"));
    private static final DateTimeFormatter YEAR_LABEL = DateTimeFormatter.ofPattern("yyyy");

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TalkRepository talkRepository;
    private final ParsingEventRepository parsingEventRepository;
    private final SpeakerNpsHistoryRepository speakerNpsHistoryRepository;
    private final SpeakerAchievementRepository speakerAchievementRepository;
    private final CfpRecommendationRepository cfpRecommendationRepository;

    @Transactional(readOnly = true)
    public ReportFiltersResponse getFilters() {
        List<RoleFilterOption> roleOptions = roleRepository.findDistinctRoleNames().stream()
                .map(name -> new RoleFilterOption(name, toKey(name)))
                .toList();
        RoleFilterGroup roleGroup = new RoleFilterGroup("Роли", "roles", roleOptions);

        List<String> regions = unionSorted(
                userRepository.findDistinctRegions(),
                parsingEventRepository.findDistinctRegions(),
                cfpRecommendationRepository.findDistinctCities()
        );
        List<String> companies = unionSorted(
                userRepository.findDistinctCompanies(),
                parsingEventRepository.findDistinctCompanies(),
                cfpRecommendationRepository.findDistinctCompanies()
        );

        List<SelectOption> parsingSourceOptions = parsingEventRepository.findDistinctSources().stream()
                .filter(source -> source != null && !source.isBlank())
                .map(this::extractSourceHost)
                .distinct()
                .sorted()
                .map(source -> new SelectOption(source, source))
                .toList();

        return new ReportFiltersResponse(
                List.of(
                        new SelectOption("Последние 30 дней", DashboardPeriod.THIRTY_DAYS.value()),
                        new SelectOption("Квартал", DashboardPeriod.QUARTER.value()),
                        new SelectOption("Год", DashboardPeriod.YEAR.value()),
                        new SelectOption("Всё время", DashboardPeriod.ALL.value())
                ),
                regions,
                companies,
                List.of(roleGroup),
                List.of(
                        new SelectOption("Внутренний", DashboardInfluence.local.name()),
                        new SelectOption("Региональный", DashboardInfluence.regional.name()),
                        new SelectOption("Федеральный", DashboardInfluence.federal.name()),
                        new SelectOption("Международный", DashboardInfluence.international.name())
                ),
                List.of(
                        new SelectOption("Неделя", "week"),
                        new SelectOption("Месяц", "month")
                ),
                parsingSourceOptions,
                List.of(
                        new SelectOption("Новые", "new"),
                        new SelectOption("Дубликаты", "duplicate"),
                        new SelectOption("Подтверждённые", "confirmed"),
                        new SelectOption("Отклонённые", "rejected")
                )
        );
    }

    @Transactional(readOnly = true)
    public SpeakerDashboardReport generateSpeakerReport(SpeakerDashboardReportRequest request) {
        SpeakerDashboardReportRequest applied = normalizeSpeakerRequest(request);
        UsersRecord user = resolveCurrentUser();

        List<TalksRecord> talks = talkRepository.findBySpeakerId(user.getId());
        List<String> roles = roleRepository.findBySpeakerId(user.getId()).stream()
                .map(record -> record.getName())
                .toList();
        List<SpeakerNpsHistoryRecord> npsHistory = speakerNpsHistoryRepository.findBySpeakerId(user.getId());
        List<SpeakerAchievementsRecord> achievements = speakerAchievementRepository.findBySpeakerId(user.getId());

        List<CfpRecommendationRepository.SpeakerRecommendationRow> allRecommendations = cfpRecommendationRepository.findBySpeakerId(user.getId());
        Map<UUID, List<String>> recommendationTags = cfpRecommendationRepository.findTagsByRecommendationIds(
                allRecommendations.stream().map(item -> item.recommendation().getId()).toList()
        );
        List<RecommendationCard> mappedRecommendations = mapRecommendationCards(allRecommendations);
        List<RecommendationCard> filteredRecommendations = filterSpeakerRecommendations(
                mappedRecommendations,
                recommendationTags,
                applied,
                roles
        );

        int currentNps = toNps100(user.getNps());
        int previousNps = resolvePreviousNps(currentNps, npsHistory);
        DashboardSeriesByPeriod seriesByPeriod = buildSeriesByPeriod(talks, currentNps);
        TrendSeries currentSeries = switch (applied.period()) {
            case THIRTY_DAYS -> seriesByPeriod.last30d();
            case QUARTER -> seriesByPeriod.quarter();
            case YEAR -> seriesByPeriod.year();
            case ALL -> seriesByPeriod.all();
        };

        List<String> tags = topTagsFromTalks(talks, 12).stream().map(TagCloudItem::label).toList();
        DashboardSpeaker speaker = new DashboardSpeaker(
                user.getId(),
                displayName(user),
                user.getUsername(),
                roles.isEmpty() ? "" : roles.getFirst(),
                roles.isEmpty() ? "" : toKey(roles.getFirst()),
                "Роли",
                "roles",
                safe(user.getRegion()),
                safe(user.getCompany()),
                parseInfluence(user.getInfluence()),
                currentNps,
                previousNps,
                talks.size(),
                latestTalkDate(talks),
                Boolean.TRUE.equals(user.getTopConferenceCandidate()),
                tags,
                Optional.ofNullable(user.getProfileCompletion()).orElse(0),
                user.getLastEvaluationDate(),
                Optional.ofNullable(user.getAudience()).orElse(0),
                buildTalkMix(talks),
                seriesByPeriod,
                buildHeatmap(talks),
                achievements.stream().map(this::mapAchievement).toList(),
                mappedRecommendations,
                Optional.ofNullable(user.getAvatarColor()).orElse("#0ea5e9")
        );

        int trendDelta = Math.abs(currentNps - previousNps);
        String trendDirection = currentNps > previousNps ? "up" : currentNps < previousNps ? "down" : "stable";

        SpeakerDashboardSummary summary = new SpeakerDashboardSummary(
                talks.size(),
                latestTalkDate(talks),
                currentNps,
                previousNps,
                trendDirection,
                trendDelta,
                Optional.ofNullable(user.getAudience()).orElse(0),
                computeActivityGrowth(currentSeries),
                countDistinctTopics(talks),
                filteredRecommendations.size()
        );

        return new SpeakerDashboardReport(
                LocalDateTime.now(),
                applied,
                speaker,
                currentSeries,
                summary,
                topTagsFromTalks(talks, 10),
                filteredRecommendations
        );
    }

    @Transactional(readOnly = true)
    public DevRelDashboardReport generateDevRelReport(DevRelDashboardReportRequest request) {
        DevRelDashboardReportRequest applied = normalizeDevRelRequest(request);

        List<UsersRecord> users = userRepository.findAll();
        Map<Long, List<String>> rolesBySpeaker = roleRepository.findRoleNamesGroupedBySpeakerIds(
                users.stream().map(UsersRecord::getId).toList()
        );
        List<Long> speakerIds = users.stream()
                .map(UsersRecord::getId)
                .filter(id -> !rolesBySpeaker.getOrDefault(id, List.of()).isEmpty())
                .toList();

        List<TalksRecord> allTalks = talkRepository.findBySpeakerIds(speakerIds);
        Map<Long, List<TalksRecord>> talksBySpeaker = allTalks.stream()
                .collect(Collectors.groupingBy(TalksRecord::getSpeakerId));

        Map<Long, List<SpeakerNpsHistoryRecord>> npsHistoryBySpeaker = speakerNpsHistoryRepository.findBySpeakerIds(speakerIds).stream()
                .collect(Collectors.groupingBy(SpeakerNpsHistoryRecord::getSpeakerId));

        List<SpeakerSnapshot> snapshots = users.stream()
                .filter(user -> speakerIds.contains(user.getId()))
                .map(user -> toSnapshot(
                        user,
                        rolesBySpeaker.getOrDefault(user.getId(), List.of()),
                        talksBySpeaker.getOrDefault(user.getId(), List.of()),
                        npsHistoryBySpeaker.getOrDefault(user.getId(), List.of())
                ))
                .filter(snapshot -> matchesDevRelFilters(snapshot, applied))
                .toList();

        List<DashboardSpeaker> rankedSpeakers = snapshots.stream()
                .sorted(Comparator
                        .comparingInt(SpeakerSnapshot::nps).reversed()
                        .thenComparingInt(SpeakerSnapshot::talksCount).reversed()
                )
                .map(snapshot -> snapshot.dashboardSpeaker)
                .toList();

        List<DashboardSpeaker> topConferenceCandidates = snapshots.stream()
                .filter(SpeakerSnapshot::topConferenceCandidate)
                .sorted(Comparator.comparingInt(SpeakerSnapshot::nps).reversed())
                .map(snapshot -> snapshot.dashboardSpeaker)
                .toList();

        List<TalksRecord> talksForSelected = snapshots.stream()
                .flatMap(snapshot -> snapshot.talks.stream())
                .toList();
        List<ParsingEventsRecord> parsingEvents = filterParsingEvents(applied, snapshots);

        DevRelParsingSection parsing = buildParsingSection(parsingEvents);
        DevRelDashboardSummary summary = new DevRelDashboardSummary(
                snapshots.size(),
                avg(snapshots.stream().map(SpeakerSnapshot::nps).toList()),
                (int) snapshots.stream().filter(SpeakerSnapshot::isRising).count(),
                parsing.parsingConfirmedRate(),
                topConferenceCandidates.size()
        );

        DevRelRatingSection rating = new DevRelRatingSection(
                rankedSpeakers,
                topConferenceCandidates,
                snapshots.isEmpty() ? 0 : Math.round((float) snapshots.stream().mapToInt(SpeakerSnapshot::talksCount).sum() / snapshots.size()),
                (int) snapshots.stream().filter(snapshot -> snapshot.influence == DashboardInfluence.international).count()
        );

        TrendSeries aggregateSeries = buildAggregateSeries(talksForSelected, applied.period(), summary.avgNps());
        List<TagCloudItem> tagCloud = buildDevRelTagCloud(talksForSelected, applied.tagWindow());
        DevRelAnalyticsSection analytics = new DevRelAnalyticsSection(
                aggregateSeries,
                new InfluenceBreakdown(
                        (int) snapshots.stream().filter(snapshot -> snapshot.influence == DashboardInfluence.local).count(),
                        (int) snapshots.stream().filter(snapshot -> snapshot.influence == DashboardInfluence.regional).count(),
                        (int) snapshots.stream().filter(snapshot -> snapshot.influence == DashboardInfluence.federal).count(),
                        (int) snapshots.stream().filter(snapshot -> snapshot.influence == DashboardInfluence.international).count()
                ),
                tagCloud,
                tagCloud.isEmpty() ? "Нет данных" : tagCloud.getFirst().label()
        );

        List<BlindSpotRow> blindRows = snapshots.stream()
                .map(this::toBlindSpotRow)
                .filter(row -> !row.reasons().isEmpty())
                .toList();
        int unevaluatedCount = (int) blindRows.stream()
                .filter(row -> row.daysWithoutReview() == null || row.daysWithoutReview() > 180)
                .count();
        int inactiveCount = (int) blindRows.stream().filter(row -> row.speaker().talksCount() < 3).count();
        int incompleteCount = (int) blindRows.stream().filter(row -> row.speaker().profileCompletion() < 70).count();
        int unevaluatedShare = snapshots.isEmpty() ? 0 : Math.round((unevaluatedCount * 100f) / snapshots.size());

        DevRelBlindSpotsSection blindSpots = new DevRelBlindSpotsSection(
                unevaluatedShare,
                inactiveCount,
                incompleteCount,
                blindRows
        );

        return new DevRelDashboardReport(
                LocalDateTime.now(),
                applied,
                summary,
                rating,
                analytics,
                blindSpots,
                parsing
        );
    }

    private UsersRecord resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private SpeakerDashboardReportRequest normalizeSpeakerRequest(SpeakerDashboardReportRequest request) {
        if (request == null) {
            return new SpeakerDashboardReportRequest(DashboardPeriod.YEAR, List.of(), List.of(), List.of(), null, 0, 100);
        }
        int npsMin = request.npsMin() == null ? 0 : Math.max(0, request.npsMin());
        int npsMax = request.npsMax() == null ? 100 : Math.min(100, request.npsMax());
        return new SpeakerDashboardReportRequest(
                request.period() == null ? DashboardPeriod.YEAR : request.period(),
                Optional.ofNullable(request.targetRegions()).orElse(List.of()),
                Optional.ofNullable(request.targetRoles()).orElse(List.of()),
                Optional.ofNullable(request.targetCompanies()).orElse(List.of()),
                request.targetInfluence(),
                Math.min(npsMin, npsMax),
                Math.max(npsMin, npsMax)
        );
    }

    private DevRelDashboardReportRequest normalizeDevRelRequest(DevRelDashboardReportRequest request) {
        if (request == null) {
            return new DevRelDashboardReportRequest(DashboardPeriod.YEAR, List.of(), List.of(), List.of(), null, 0, 100, null, "week", null, null);
        }
        int npsMin = request.npsMin() == null ? 0 : Math.max(0, request.npsMin());
        int npsMax = request.npsMax() == null ? 100 : Math.min(100, request.npsMax());
        return new DevRelDashboardReportRequest(
                request.period() == null ? DashboardPeriod.YEAR : request.period(),
                Optional.ofNullable(request.regions()).orElse(List.of()),
                Optional.ofNullable(request.roles()).orElse(List.of()),
                Optional.ofNullable(request.companies()).orElse(List.of()),
                request.influence(),
                Math.min(npsMin, npsMax),
                Math.max(npsMin, npsMax),
                request.speakerSearch(),
                request.tagWindow() == null || request.tagWindow().isBlank() ? "week" : request.tagWindow(),
                request.parsingSource(),
                request.parsingStatus()
        );
    }

    private int resolvePreviousNps(int currentNps, List<SpeakerNpsHistoryRecord> history) {
        if (history.size() < 2) {
            return currentNps;
        }
        return toNps100(history.get(1).getNps());
    }

    private DashboardSeriesByPeriod buildSeriesByPeriod(List<TalksRecord> talks, int nps) {
        return new DashboardSeriesByPeriod(
                buildSeries(talks, DashboardPeriod.THIRTY_DAYS, nps),
                buildSeries(talks, DashboardPeriod.QUARTER, nps),
                buildSeries(talks, DashboardPeriod.YEAR, nps),
                buildSeries(talks, DashboardPeriod.ALL, nps)
        );
    }

    private TrendSeries buildSeries(List<TalksRecord> talks, DashboardPeriod period, int nps) {
        if (talks.isEmpty()) {
            return EMPTY_TREND;
        }

        List<LocalDate> buckets = switch (period) {
            case THIRTY_DAYS -> bucketStarts(4, ChronoUnit.WEEKS, ChronoUnit.WEEKS);
            case QUARTER -> bucketStarts(3, ChronoUnit.MONTHS, ChronoUnit.MONTHS);
            case YEAR -> bucketStarts(12, ChronoUnit.MONTHS, ChronoUnit.MONTHS);
            case ALL -> bucketStartsAllYears(talks);
        };

        List<String> labels = new ArrayList<>();
        List<Integer> talksByBucket = new ArrayList<>();
        List<Integer> npsByBucket = new ArrayList<>();
        for (int i = 0; i < buckets.size(); i++) {
            LocalDate start = buckets.get(i);
            LocalDate end = i + 1 < buckets.size() ? buckets.get(i + 1) : LocalDate.now().plusDays(1);
            int count = (int) talks.stream()
                    .filter(talk -> talk.getTalkDate() != null)
                    .filter(talk -> {
                        LocalDate day = talk.getTalkDate().toLocalDate();
                        return (day.isEqual(start) || day.isAfter(start)) && day.isBefore(end);
                    })
                    .count();

            labels.add(labelForBucket(start, period, i));
            talksByBucket.add(count);
            npsByBucket.add(nps);
        }

        return new TrendSeries(labels, talksByBucket, npsByBucket);
    }

    private TrendSeries buildAggregateSeries(List<TalksRecord> talks, DashboardPeriod period, int avgNps) {
        if (talks.isEmpty()) {
            return EMPTY_TREND;
        }
        return buildSeries(talks, period == null ? DashboardPeriod.YEAR : period, avgNps);
    }

    private List<LocalDate> bucketStarts(int size, ChronoUnit backStep, ChronoUnit truncateUnit) {
        List<LocalDate> result = new ArrayList<>();
        LocalDate cursor = LocalDate.now();
        for (int i = size - 1; i >= 0; i--) {
            LocalDate point = cursor.minus(i, backStep);
            if (truncateUnit == ChronoUnit.WEEKS) {
                point = point.minusDays(point.getDayOfWeek().getValue() - 1L);
            } else {
                point = point.withDayOfMonth(1);
            }
            result.add(point);
        }
        return result;
    }

    private List<LocalDate> bucketStartsAllYears(List<TalksRecord> talks) {
        LocalDate min = talks.stream()
                .map(TalksRecord::getTalkDate)
                .filter(Objects::nonNull)
                .map(LocalDateTime::toLocalDate)
                .min(LocalDate::compareTo)
                .orElse(LocalDate.now());
        List<LocalDate> years = new ArrayList<>();
        int start = min.getYear();
        int end = LocalDate.now().getYear();
        for (int year = start; year <= end; year++) {
            years.add(LocalDate.of(year, 1, 1));
        }
        return years;
    }

    private String labelForBucket(LocalDate start, DashboardPeriod period, int index) {
        return switch (period) {
            case THIRTY_DAYS -> "W" + (index + 1);
            case QUARTER, YEAR -> start.format(MONTH_LABEL);
            case ALL -> start.format(YEAR_LABEL);
        };
    }

    private int computeActivityGrowth(TrendSeries series) {
        if (series.talks().size() < 2) {
            return 0;
        }
        int midpoint = Math.max(1, series.talks().size() / 2);
        int previous = series.talks().subList(0, midpoint).stream().mapToInt(Integer::intValue).sum();
        int current = series.talks().subList(midpoint, series.talks().size()).stream().mapToInt(Integer::intValue).sum();
        if (previous == 0) {
            return current == 0 ? 0 : 100;
        }
        return Math.round((current - previous) * 100f / previous);
    }

    private int countDistinctTopics(List<TalksRecord> talks) {
        return (int) talks.stream()
                .map(TalksRecord::getTopic)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toLowerCase)
                .distinct()
                .count();
    }

    private List<TagCloudItem> topTagsFromTalks(List<TalksRecord> talks, int limit) {
        Map<String, Integer> counts = new HashMap<>();
        for (TalksRecord talk : talks) {
            if (talk.getTopic() == null) {
                continue;
            }
            for (String token : talk.getTopic().toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
                if (token.length() < 3) {
                    continue;
                }
                counts.merge(token, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> new TagCloudItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private InfluenceBreakdown buildTalkMix(List<TalksRecord> talks) {
        int local = 0;
        int regional = 0;
        int federal = 0;
        int international = 0;

        for (TalksRecord talk : talks) {
            String type = Optional.ofNullable(talk.getConferenceType()).orElse("");
            switch (type.toUpperCase(Locale.ROOT)) {
                case "MEETUP" -> local++;
                case "WORKSHOP" -> regional++;
                case "CONFERENCE" -> federal++;
                default -> international++;
            }
        }
        return new InfluenceBreakdown(local, regional, federal, international);
    }

    private List<Integer> buildHeatmap(List<TalksRecord> talks) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(13);
        Map<LocalDate, Integer> byDate = talks.stream()
                .filter(talk -> talk.getTalkDate() != null)
                .collect(Collectors.groupingBy(
                        talk -> talk.getTalkDate().toLocalDate(),
                        Collectors.summingInt(item -> 1)
                ));

        List<Integer> values = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            int count = byDate.getOrDefault(day, 0);
            values.add(Math.min(count, 4));
        }
        return values;
    }

    private Achievement mapAchievement(SpeakerAchievementsRecord record) {
        return new Achievement(
                record.getId().toString(),
                record.getTitle(),
                record.getDescription(),
                record.getCurrentValue(),
                record.getTargetValue()
        );
    }

    private List<RecommendationCard> mapRecommendationCards(List<CfpRecommendationRepository.SpeakerRecommendationRow> rows) {
        return rows.stream()
                .map(row -> new RecommendationCard(
                        row.recommendation().getId().toString(),
                        row.recommendation().getTitle(),
                        row.recommendation().getCity(),
                        parseInfluence(row.recommendation().getInfluenceLevel()),
                        row.recommendation().getTopic(),
                        row.recommendation().getDeadline(),
                        Optional.ofNullable(row.matchScore()).orElse(0),
                        row.recommendation().getHostCompany(),
                        row.recommendation().getUrl()
                ))
                .toList();
    }

    private List<RecommendationCard> filterSpeakerRecommendations(
            List<RecommendationCard> cards,
            Map<UUID, List<String>> tagsByRecommendation,
            SpeakerDashboardReportRequest request,
            List<String> speakerRoles
    ) {
        if (cards.isEmpty()) {
            return cards;
        }

        Predicate<RecommendationCard> rolePredicate = card -> {
            if (request.targetRoles().isEmpty()) {
                return true;
            }
            Set<String> roleKeys = speakerRoles.stream().map(this::toKey).collect(Collectors.toSet());
            return request.targetRoles().stream().anyMatch(roleKeys::contains);
        };

        return cards.stream()
                .filter(card -> request.targetRegions().isEmpty() || request.targetRegions().contains(card.city()))
                .filter(card -> request.targetCompanies().isEmpty() || request.targetCompanies().contains(card.hostCompany()))
                .filter(card -> request.targetInfluence() == null || request.targetInfluence() == card.level())
                .filter(card -> card.match() >= request.npsMin() && card.match() <= request.npsMax())
                .filter(rolePredicate)
                .toList();
    }

    private SpeakerSnapshot toSnapshot(
            UsersRecord user,
            List<String> roles,
            List<TalksRecord> talks,
            List<SpeakerNpsHistoryRecord> history
    ) {
        int nps = toNps100(user.getNps());
        int previousNps = resolvePreviousNps(nps, history);
        DashboardSeriesByPeriod series = buildSeriesByPeriod(talks, nps);
        DashboardSpeaker speaker = new DashboardSpeaker(
                user.getId(),
                displayName(user),
                user.getUsername(),
                roles.isEmpty() ? "" : roles.getFirst(),
                roles.isEmpty() ? "" : toKey(roles.getFirst()),
                "Роли",
                "roles",
                safe(user.getRegion()),
                safe(user.getCompany()),
                parseInfluence(user.getInfluence()),
                nps,
                previousNps,
                talks.size(),
                latestTalkDate(talks),
                Boolean.TRUE.equals(user.getTopConferenceCandidate()),
                topTagsFromTalks(talks, 12).stream().map(TagCloudItem::label).toList(),
                Optional.ofNullable(user.getProfileCompletion()).orElse(0),
                user.getLastEvaluationDate(),
                Optional.ofNullable(user.getAudience()).orElse(0),
                buildTalkMix(talks),
                series,
                buildHeatmap(talks),
                List.of(),
                List.of(),
                Optional.ofNullable(user.getAvatarColor()).orElse("#0ea5e9")
        );

        return new SpeakerSnapshot(
                user.getId(),
                speaker,
                talks,
                roles,
                parseInfluence(user.getInfluence()),
                nps,
                previousNps
        );
    }

    private boolean matchesDevRelFilters(SpeakerSnapshot snapshot, DevRelDashboardReportRequest request) {
        DashboardSpeaker speaker = snapshot.dashboardSpeaker;

        if (!request.regions().isEmpty() && !request.regions().contains(speaker.region())) {
            return false;
        }
        if (!request.companies().isEmpty() && !request.companies().contains(speaker.company())) {
            return false;
        }
        if (request.influence() != null && request.influence() != speaker.influence()) {
            return false;
        }
        if (speaker.nps() < request.npsMin() || speaker.nps() > request.npsMax()) {
            return false;
        }
        if (!request.roles().isEmpty()) {
            Set<String> keys = snapshot.roles.stream().map(this::toKey).collect(Collectors.toSet());
            boolean matched = request.roles().stream().anyMatch(keys::contains);
            if (!matched) {
                return false;
            }
        }
        if (request.speakerSearch() != null && !request.speakerSearch().isBlank()) {
            String query = request.speakerSearch().toLowerCase(Locale.ROOT);
            boolean matched = speaker.name().toLowerCase(Locale.ROOT).contains(query)
                    || speaker.username().toLowerCase(Locale.ROOT).contains(query)
                    || snapshot.roles.stream().anyMatch(role -> role.toLowerCase(Locale.ROOT).contains(query))
                    || speaker.tags().stream().anyMatch(tag -> tag.toLowerCase(Locale.ROOT).contains(query));
            if (!matched) {
                return false;
            }
        }
        if (!isWithinPeriod(snapshot.talks, request.period())) {
            return false;
        }
        return true;
    }

    private boolean isWithinPeriod(List<TalksRecord> talks, DashboardPeriod period) {
        if (period == null || period == DashboardPeriod.ALL) {
            return true;
        }
        LocalDateTime border = switch (period) {
            case THIRTY_DAYS -> LocalDateTime.now().minusDays(30);
            case QUARTER -> LocalDateTime.now().minusDays(90);
            case YEAR -> LocalDateTime.now().minusDays(365);
            case ALL -> LocalDateTime.MIN;
        };
        return talks.stream().anyMatch(talk -> talk.getTalkDate() != null && !talk.getTalkDate().isBefore(border));
    }

    private List<ParsingEventsRecord> filterParsingEvents(DevRelDashboardReportRequest request, List<SpeakerSnapshot> snapshots) {
        String status = request.parsingStatus() == null ? null : toParsingStatusString(request.parsingStatus());
        List<ParsingEventsRecord> events = parsingEventRepository.findBySourceAndStatus(request.parsingSource(), status);
        Set<Long> speakerIds = snapshots.stream().map(SpeakerSnapshot::id).collect(Collectors.toSet());
        LocalDate borderDate = switch (request.period()) {
            case THIRTY_DAYS -> LocalDate.now().minusDays(30);
            case QUARTER -> LocalDate.now().minusDays(90);
            case YEAR -> LocalDate.now().minusDays(365);
            case ALL -> null;
        };

        return events.stream()
                .filter(event -> borderDate == null || !event.getEventDate().isBefore(borderDate))
                .filter(event -> request.regions().isEmpty() || request.regions().contains(safe(event.getRegion())))
                .filter(event -> request.companies().isEmpty() || request.companies().contains(safe(event.getCompany())))
                .filter(event -> event.getSpeakerId() == null || speakerIds.contains(event.getSpeakerId()))
                .toList();
    }

    private DevRelParsingSection buildParsingSection(List<ParsingEventsRecord> filteredEvents) {
        int newEvents = (int) filteredEvents.stream().filter(event -> "new".equalsIgnoreCase(event.getStatus())).count();
        int duplicateEvents = (int) filteredEvents.stream().filter(event -> "duplicate".equalsIgnoreCase(event.getStatus())).count();
        int confirmed = (int) filteredEvents.stream().filter(event -> "confirmed".equalsIgnoreCase(event.getStatus())).count();
        int rejected = (int) filteredEvents.stream().filter(event -> "rejected".equalsIgnoreCase(event.getStatus())).count();
        int reviewed = confirmed + rejected;
        int confirmedRate = reviewed == 0 ? 0 : Math.round((confirmed * 100f) / reviewed);

        Map<String, Integer> bySource = new LinkedHashMap<>();
        for (ParsingEventsRecord event : filteredEvents) {
            String source = extractSourceHost(event.getSourceUrl());
            bySource.merge(source, 1, Integer::sum);
        }
        List<ParsingSourceBucket> sourceBuckets = bySource.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(entry -> new ParsingSourceBucket(entry.getKey(), entry.getValue()))
                .toList();

        List<ParsingEvent> events = filteredEvents.stream()
                .sorted(Comparator.comparing(ParsingEventsRecord::getCreatedAt).reversed())
                .limit(100)
                .map(this::toParsingEvent)
                .toList();

        return new DevRelParsingSection(
                newEvents,
                duplicateEvents,
                confirmedRate,
                sourceBuckets,
                events
        );
    }

    private List<TagCloudItem> buildDevRelTagCloud(List<TalksRecord> talks, String tagWindow) {
        LocalDateTime border = "month".equalsIgnoreCase(tagWindow)
                ? LocalDateTime.now().minusDays(30)
                : LocalDateTime.now().minusDays(7);

        List<TalksRecord> scopedTalks = talks.stream()
                .filter(talk -> talk.getTalkDate() != null && !talk.getTalkDate().isBefore(border))
                .toList();
        return topTagsFromTalks(scopedTalks, 10);
    }

    private BlindSpotRow toBlindSpotRow(SpeakerSnapshot snapshot) {
        DashboardSpeaker speaker = snapshot.dashboardSpeaker;
        Integer daysWithoutReview = speaker.lastEvaluationDate() == null
                ? null
                : (int) ChronoUnit.DAYS.between(speaker.lastEvaluationDate().toLocalDate(), LocalDate.now());

        List<String> reasons = new ArrayList<>();
        if (daysWithoutReview == null || daysWithoutReview > 180) {
            reasons.add("Нет свежей оценки");
        }
        if (speaker.profileCompletion() < 70) {
            reasons.add("Профиль заполнен не полностью");
        }
        if (speaker.talksCount() < 3) {
            reasons.add("Низкая активность");
        }
        if (speaker.nps() < speaker.previousNps()) {
            reasons.add("Снижение NPS");
        }

        return new BlindSpotRow(speaker, reasons, daysWithoutReview);
    }

    private ParsingEvent toParsingEvent(ParsingEventsRecord record) {
        return new ParsingEvent(
                record.getId().toString(),
                record.getConference(),
                Optional.ofNullable(record.getSpeakerId()).orElse(0L),
                safe(record.getSpeakerName()),
                safe(record.getCompany()),
                extractSourceHost(record.getSourceUrl()),
                fromParsingStatus(record.getStatus()),
                safe(record.getRegion()),
                record.getEventDate().atStartOfDay(),
                safe(record.getNote())
        );
    }

    private String toParsingStatusString(ParsingStatus status) {
        return switch (status) {
            case NEW -> "new";
            case duplicate -> "duplicate";
            case confirmed -> "confirmed";
            case rejected -> "rejected";
        };
    }

    private ParsingStatus fromParsingStatus(String status) {
        return switch (status == null ? "" : status.toLowerCase(Locale.ROOT)) {
            case "duplicate" -> ParsingStatus.duplicate;
            case "confirmed" -> ParsingStatus.confirmed;
            case "rejected" -> ParsingStatus.rejected;
            default -> ParsingStatus.NEW;
        };
    }

    private DashboardInfluence parseInfluence(String value) {
        if (value == null) {
            return DashboardInfluence.local;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "regional" -> DashboardInfluence.regional;
            case "federal" -> DashboardInfluence.federal;
            case "international" -> DashboardInfluence.international;
            default -> DashboardInfluence.local;
        };
    }

    private String displayName(UsersRecord user) {
        String full = List.of(
                        safe(user.getFirstName()),
                        safe(user.getLastName()),
                        safe(user.getSurName())
                ).stream()
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "))
                .trim();
        return full.isBlank() ? safe(user.getUsername()) : full;
    }

    private LocalDateTime latestTalkDate(List<TalksRecord> talks) {
        return talks.stream()
                .map(TalksRecord::getTalkDate)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private List<String> unionSorted(Collection<String>... sources) {
        HashSet<String> values = new HashSet<>();
        for (Collection<String> source : sources) {
            for (String value : source) {
                if (value != null && !value.isBlank()) {
                    values.add(value);
                }
            }
        }
        return values.stream().sorted().toList();
    }

    private int avg(List<Integer> values) {
        if (values.isEmpty()) {
            return 0;
        }
        return Math.round((float) values.stream().mapToInt(Integer::intValue).sum() / values.size());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String toKey(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private Integer toNps100(BigDecimal nps0to10) {
        if (nps0to10 == null) {
            return 0;
        }
        return nps0to10.multiply(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private String extractSourceHost(String sourceUrl) {
        if (sourceUrl == null || sourceUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(sourceUrl);
            return uri.getHost() == null ? sourceUrl : uri.getHost();
        } catch (Exception ignored) {
            return sourceUrl;
        }
    }

    private record SpeakerSnapshot(
            Long id,
            DashboardSpeaker dashboardSpeaker,
            List<TalksRecord> talks,
            List<String> roles,
            DashboardInfluence influence,
            int nps,
            int previousNps
    ) {
        int talksCount() {
            return dashboardSpeaker.talksCount();
        }

        boolean topConferenceCandidate() {
            return Boolean.TRUE.equals(dashboardSpeaker.topConferenceCandidate());
        }

        boolean isRising() {
            return nps > previousNps;
        }
    }
}
