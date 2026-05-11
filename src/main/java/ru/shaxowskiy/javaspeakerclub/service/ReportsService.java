package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels.*;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerRoles;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Talks;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Users;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerRolesRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;
import ru.shaxowskiy.javaspeakerclub.repository.RoleRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportsService {

    private static final TrendSeries EMPTY_TREND = new TrendSeries(List.of("all"), List.of(0), List.of(0));
    private static final InfluenceBreakdown EMPTY_INFLUENCE = new InfluenceBreakdown(0, 0, 0, 0);

    private final DSLContext dsl;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final TalkRepository talkRepository;

    @Transactional(readOnly = true)
    public ReportFiltersResponse getFilters() {
        log.info("Building report filter options");
        List<RoleFilterOption> roleOptions = roleRepository.findAll().stream()
                .map(record -> new RoleFilterOption(record.getName(), toKey(record.getName())))
                .toList();

        RoleFilterGroup roleGroup = new RoleFilterGroup("Роли", "roles", roleOptions);

        List<SelectOption> periodOptions = List.of(
                new SelectOption("Последние 30 дней", DashboardPeriod.THIRTY_DAYS.value()),
                new SelectOption("Квартал", DashboardPeriod.QUARTER.value()),
                new SelectOption("Год", DashboardPeriod.YEAR.value()),
                new SelectOption("Всё время", DashboardPeriod.ALL.value())
        );

        List<SelectOption> influenceOptions = List.of(
                new SelectOption("Local", DashboardInfluence.local.name()),
                new SelectOption("Regional", DashboardInfluence.regional.name()),
                new SelectOption("Federal", DashboardInfluence.federal.name()),
                new SelectOption("International", DashboardInfluence.international.name())
        );

        List<SelectOption> tagWindowOptions = List.of(
                new SelectOption("Неделя", "week"),
                new SelectOption("Месяц", "month")
        );

        List<SelectOption> parsingStatusOptions = List.of(
                new SelectOption("Новые", ParsingStatus.NEW.name().toLowerCase(Locale.ROOT)),
                new SelectOption("Дубликаты", ParsingStatus.duplicate.name()),
                new SelectOption("Подтверждённые", ParsingStatus.confirmed.name()),
                new SelectOption("Отклонённые", ParsingStatus.rejected.name())
        );

        ReportFiltersResponse response = new ReportFiltersResponse(
                periodOptions,
                List.of(), // regions
                List.of(), // companies
                List.of(roleGroup),
                influenceOptions,
                tagWindowOptions,
                List.of(), // parsingSources
                parsingStatusOptions
        );
        log.info("Built report filters: roleOptions={}, parsingOptions={}", roleOptions.size(), parsingStatusOptions.size());
        return response;
    }

    @Transactional(readOnly = true)
    public SpeakerDashboardReport generateSpeakerReport(SpeakerDashboardReportRequest request) {
        log.info("Generating speaker dashboard report, request={}", request);
        List<SpeakerAggregate> aggregates = fetchSpeakerAggregates();
        if (aggregates.isEmpty()) {
            log.info("No speaker aggregates found");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No speakers found");
        }

        SpeakerAggregate target = aggregates.getFirst();
        log.info("Selected speaker id={}, username={}", target.id(), target.username());
        List<UUID> talkIds = talkRepository.findBySpeakerId(target.id()).stream()
                .map(TalksRecord::getId)
                .toList();

        List<String> roleNames = roleRepository.findBySpeakerId(target.id()).stream()
                .map(SpeakerRolesRecord::getName)
                .toList();

        DashboardSpeaker speaker = buildSpeaker(target, roleNames, talkIds.size());

        TrendSeries currentSeries = buildTrendSeries(talkIds.size(), target.nps());
        DashboardSeriesByPeriod seriesByPeriod = new DashboardSeriesByPeriod(currentSeries, currentSeries, currentSeries, currentSeries);
        speaker = new DashboardSpeaker(
                speaker.id(),
                speaker.name(),
                speaker.username(),
                speaker.role(),
                speaker.roleKey(),
                speaker.roleGroup(),
                speaker.roleGroupKey(),
                speaker.region(),
                speaker.company(),
                speaker.influence(),
                speaker.nps(),
                speaker.previousNps(),
                speaker.talksCount(),
                speaker.lastTalkDate(),
                speaker.topConferenceCandidate(),
                speaker.tags(),
                speaker.profileCompletion(),
                speaker.lastEvaluationDate(),
                speaker.audience(),
                speaker.talkMix(),
                seriesByPeriod,
                speaker.heatmap(),
                speaker.achievements(),
                speaker.recommendations(),
                speaker.avatarColor()
        );

        SpeakerDashboardSummary summary = new SpeakerDashboardSummary(
                target.talksCount(),
                target.lastTalkDate(),
                target.nps(),
                target.nps(),
                "stable",
                0,
                0,
                0,
                talkIds.size(),
                0
        );

        SpeakerDashboardReportRequest appliedFilters = request != null ? request : new SpeakerDashboardReportRequest(null, List.of(), List.of(), List.of(), null, null, null);

        SpeakerDashboardReport report = new SpeakerDashboardReport(
                LocalDateTime.now(),
                appliedFilters,
                speaker,
                currentSeries,
                summary,
                List.of(),
                List.of()
        );
        log.info("Speaker dashboard report built for speakerId={}, talksCount={}", target.id(), talkIds.size());
        return report;
    }

    @Transactional(readOnly = true)
    public DevRelDashboardReport generateDevRelReport(DevRelDashboardReportRequest request) {
        log.info("Generating DevRel dashboard report, request={}", request);
        List<SpeakerAggregate> aggregates = fetchSpeakerAggregates();
        Map<Long, List<String>> rolesBySpeaker = fetchRolesBySpeaker();

        int activeSpeakers = (int) aggregates.stream().filter(a -> a.talksCount() > 0).count();
        int avgNps = computeAverageNps();
        int totalTalks = aggregates.stream().mapToInt(SpeakerAggregate::talksCount).sum();
        int avgTalks = activeSpeakers > 0 ? Math.round((float) totalTalks / activeSpeakers) : 0;

        List<DashboardSpeaker> rankedSpeakers = aggregates.stream()
                .sorted(Comparator.comparingInt(SpeakerAggregate::talksCount).reversed())
                .limit(50)
                .map(agg -> buildSpeaker(agg, rolesBySpeaker.getOrDefault(agg.id(), List.of()), agg.talksCount()))
                .toList();

        TrendSeries aggregateSeries = aggregates.isEmpty()
                ? EMPTY_TREND
                : new TrendSeries(
                List.of("all"),
                List.of(totalTalks),
                List.of(avgNps)
        );

        DevRelDashboardSummary summary = new DevRelDashboardSummary(
                activeSpeakers,
                avgNps,
                0,
                0,
                0
        );

        DevRelRatingSection rating = new DevRelRatingSection(
                rankedSpeakers,
                List.of(),
                avgTalks,
                0
        );

        DevRelAnalyticsSection analytics = new DevRelAnalyticsSection(
                aggregateSeries,
                EMPTY_INFLUENCE,
                List.of(),
                ""
        );

        DevRelBlindSpotsSection blindSpots = new DevRelBlindSpotsSection(
                0,
                0,
                0,
                List.of()
        );

        DevRelParsingSection parsing = new DevRelParsingSection(
                0,
                0,
                0,
                List.of(),
                List.of()
        );

        DevRelDashboardReportRequest appliedFilters = request != null ? request
                : new DevRelDashboardReportRequest(null, List.of(), List.of(), List.of(), null, null, null, null, null, null, null);

        DevRelDashboardReport report = new DevRelDashboardReport(
                LocalDateTime.now(),
                appliedFilters,
                summary,
                rating,
                analytics,
                blindSpots,
                parsing
        );
        log.info("DevRel dashboard report built: activeSpeakers={}, totalTalks={}", activeSpeakers, totalTalks);
        return report;
    }

    private List<SpeakerAggregate> fetchSpeakerAggregates() {
        log.info("Fetching speaker aggregates");
        List<SpeakerAggregate> aggregates = dsl.select(
                        Users.USERS.ID,
                        Users.USERS.USERNAME,
                        Users.USERS.NPS,
                        DSL.count(Talks.TALKS.ID).as("talks_count"),
                        DSL.max(Talks.TALKS.TALK_DATE).as("last_talk_date")
                )
                .from(Users.USERS)
                .leftJoin(Talks.TALKS).on(Talks.TALKS.SPEAKER_ID.eq(Users.USERS.ID))
                .groupBy(Users.USERS.ID, Users.USERS.USERNAME, Users.USERS.NPS)
                .orderBy(DSL.count(Talks.TALKS.ID).desc())
                .fetch(record -> new SpeakerAggregate(
                        record.get(Users.USERS.ID),
                        record.get(Users.USERS.USERNAME),
                        toNps100(record.get(Users.USERS.NPS)),
                        Optional.ofNullable(record.get("talks_count", Integer.class)).orElse(0),
                        record.get("last_talk_date", LocalDateTime.class)
                ));
        log.info("Fetched {} speaker aggregates", aggregates.size());
        return aggregates;
    }

    private Map<Long, List<String>> fetchRolesBySpeaker() {
        log.info("Fetching roles grouped by speaker");
        Map<Long, List<String>> roles = dsl.select(SpeakerRoles.SPEAKER_ROLES.SPEAKER_ID, SpeakerRoles.SPEAKER_ROLES.NAME)
                .from(SpeakerRoles.SPEAKER_ROLES)
                .fetchGroups(SpeakerRoles.SPEAKER_ROLES.SPEAKER_ID, SpeakerRoles.SPEAKER_ROLES.NAME);
        log.info("Fetched roles for {} speakers", roles.size());
        return roles;
    }

    private TrendSeries buildTrendSeries(int talksCount, Integer nps) {
        log.info("Building trend series with talksCount={}, nps={}", talksCount, nps);
        return new TrendSeries(
                List.of("all"),
                List.of(talksCount),
                List.of(Optional.ofNullable(nps).orElse(0))
        );
    }

    private DashboardSpeaker buildSpeaker(SpeakerAggregate aggregate, List<String> roles, int talksCount) {
        log.info("Building dashboard speaker for id={}, roles={}", aggregate.id(), roles);
        String role = roles.isEmpty() ? "" : roles.get(0);
        String roleKey = role.isBlank() ? "" : toKey(role);
        String roleGroup = role.isBlank() ? "" : "default";
        String roleGroupKey = roleGroup.isBlank() ? "" : toKey(roleGroup);

        return new DashboardSpeaker(
                aggregate.id(),
                aggregate.username(),
                aggregate.username(),
                role,
                roleKey,
                roleGroup,
                roleGroupKey,
                "",
                "",
                DashboardInfluence.local,
                aggregate.nps(),
                aggregate.nps(),
                talksCount,
                aggregate.lastTalkDate(),
                false,
                List.of(),
                0,
                null,
                0,
                EMPTY_INFLUENCE,
                new DashboardSeriesByPeriod(EMPTY_TREND, EMPTY_TREND, EMPTY_TREND, EMPTY_TREND),
                List.of(),
                List.of(),
                List.of(),
                "#0ea5e9"
        );
    }

    private int computeAverageNps() {
        log.info("Computing average NPS");
        BigDecimal avg = dsl.select(DSL.avg(Users.USERS.NPS))
                .from(Users.USERS)
                .fetchOneInto(BigDecimal.class);
        int result = toNps100(avg);
        log.info("Average NPS (0-100)={}", result);
        return result;
    }

    private String toKey(String value) {
        log.info("Converting value to key, value={}", value);
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private Integer toNps100(BigDecimal nps0to10) {
        log.info("Converting NPS to 0-100, source={}", nps0to10);
        if (nps0to10 == null) {
            return 0;
        }
        return nps0to10.multiply(BigDecimal.TEN).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private record SpeakerAggregate(Long id, String username, Integer nps, int talksCount, LocalDateTime lastTalkDate) {
    }
}
