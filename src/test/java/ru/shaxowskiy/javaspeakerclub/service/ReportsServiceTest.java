package ru.shaxowskiy.javaspeakerclub.service;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.CfpRecommendations;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.ParsingEvents;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerAchievements;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerCfpRecommendations;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerNpsHistory;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerRoles;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Talks;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Users;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportsServiceTest {

    @Autowired
    private ReportsService reportsService;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void setUp() {
        dsl.deleteFrom(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS).execute();
        dsl.deleteFrom(SpeakerAchievements.SPEAKER_ACHIEVEMENTS).execute();
        dsl.deleteFrom(SpeakerNpsHistory.SPEAKER_NPS_HISTORY).execute();
        dsl.deleteFrom(ParsingEvents.PARSING_EVENTS).execute();
        dsl.deleteFrom(SpeakerRoles.SPEAKER_ROLES).execute();
        dsl.deleteFrom(Talks.TALKS).execute();
        dsl.deleteFrom(CfpRecommendations.CFP_RECOMMENDATIONS).execute();
        dsl.deleteFrom(Users.USERS).execute();

        dsl.insertInto(Users.USERS)
                .set(Users.USERS.ID, 1L)
                .set(Users.USERS.USERNAME, "alice")
                .set(Users.USERS.PASSWORD, "password")
                .set(Users.USERS.FIRST_NAME, "Alice")
                .set(Users.USERS.LAST_NAME, "Ivanova")
                .set(Users.USERS.NPS, new BigDecimal("8.6"))
                .set(Users.USERS.REGION, "Москва")
                .set(Users.USERS.COMPANY, "Aurora Tech")
                .set(Users.USERS.INFLUENCE, "federal")
                .set(Users.USERS.PROFILE_COMPLETION, 92)
                .set(Users.USERS.AUDIENCE, 540)
                .set(Users.USERS.TOP_CONFERENCE_CANDIDATE, true)
                .set(Users.USERS.LAST_EVALUATION_DATE, LocalDateTime.now().minusDays(45))
                .execute();

        dsl.insertInto(Users.USERS)
                .set(Users.USERS.ID, 2L)
                .set(Users.USERS.USERNAME, "bob")
                .set(Users.USERS.PASSWORD, "password")
                .set(Users.USERS.NPS, new BigDecimal("7.2"))
                .set(Users.USERS.REGION, "Казань")
                .set(Users.USERS.COMPANY, "Delta Data")
                .set(Users.USERS.INFLUENCE, "regional")
                .set(Users.USERS.PROFILE_COMPLETION, 66)
                .set(Users.USERS.AUDIENCE, 260)
                .set(Users.USERS.TOP_CONFERENCE_CANDIDATE, false)
                .set(Users.USERS.LAST_EVALUATION_DATE, LocalDateTime.now().minusDays(220))
                .execute();

        dsl.insertInto(SpeakerRoles.SPEAKER_ROLES)
                .set(SpeakerRoles.SPEAKER_ROLES.NAME, "Backend Architect")
                .set(SpeakerRoles.SPEAKER_ROLES.SPEAKER_ID, 1L)
                .execute();
        dsl.insertInto(SpeakerRoles.SPEAKER_ROLES)
                .set(SpeakerRoles.SPEAKER_ROLES.NAME, "Cloud Advocate")
                .set(SpeakerRoles.SPEAKER_ROLES.SPEAKER_ID, 2L)
                .execute();

        dsl.insertInto(Talks.TALKS)
                .set(Talks.TALKS.ID, UUID.fromString("11111111-1111-4111-8111-111111111111"))
                .set(Talks.TALKS.SPEAKER_ID, 1L)
                .set(Talks.TALKS.TOPIC, "Modern Java and Spring")
                .set(Talks.TALKS.TALK_DATE, LocalDateTime.now().minusDays(10))
                .set(Talks.TALKS.CONFERENCE_TYPE, "CONFERENCE")
                .set(Talks.TALKS.FORMAT, "ONLINE")
                .set(Talks.TALKS.ACTIVITY_NAME, "Java Summit")
                .set(Talks.TALKS.ACTIVITY_DATE, LocalDateTime.now().minusDays(11))
                .execute();
        dsl.insertInto(Talks.TALKS)
                .set(Talks.TALKS.ID, UUID.fromString("22222222-2222-4222-8222-222222222222"))
                .set(Talks.TALKS.SPEAKER_ID, 1L)
                .set(Talks.TALKS.TOPIC, "Cloud Native Patterns")
                .set(Talks.TALKS.TALK_DATE, LocalDateTime.now().minusDays(35))
                .set(Talks.TALKS.CONFERENCE_TYPE, "WORKSHOP")
                .set(Talks.TALKS.FORMAT, "OFFLINE")
                .set(Talks.TALKS.ACTIVITY_NAME, "Cloud Days")
                .set(Talks.TALKS.ACTIVITY_DATE, LocalDateTime.now().minusDays(36))
                .execute();
        dsl.insertInto(Talks.TALKS)
                .set(Talks.TALKS.ID, UUID.fromString("33333333-3333-4333-8333-333333333333"))
                .set(Talks.TALKS.SPEAKER_ID, 2L)
                .set(Talks.TALKS.TOPIC, "Data Pipelines")
                .set(Talks.TALKS.TALK_DATE, LocalDateTime.now().minusDays(15))
                .set(Talks.TALKS.CONFERENCE_TYPE, "MEETUP")
                .set(Talks.TALKS.FORMAT, "ONLINE")
                .set(Talks.TALKS.ACTIVITY_NAME, "Data Meetup")
                .set(Talks.TALKS.ACTIVITY_DATE, LocalDateTime.now().minusDays(15))
                .execute();

        dsl.insertInto(SpeakerNpsHistory.SPEAKER_NPS_HISTORY)
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SPEAKER_ID, 1L)
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.NPS, new BigDecimal("7.9"))
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.MEASURED_AT, LocalDateTime.now().minusMonths(6))
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SOURCE, "survey")
                .execute();
        dsl.insertInto(SpeakerNpsHistory.SPEAKER_NPS_HISTORY)
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SPEAKER_ID, 1L)
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.NPS, new BigDecimal("8.6"))
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.MEASURED_AT, LocalDateTime.now().minusDays(20))
                .set(SpeakerNpsHistory.SPEAKER_NPS_HISTORY.SOURCE, "survey")
                .execute();

        dsl.insertInto(SpeakerAchievements.SPEAKER_ACHIEVEMENTS)
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.ID, UUID.fromString("44444444-4444-4444-8444-444444444444"))
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.SPEAKER_ID, 1L)
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.TITLE, "Публичные выступления")
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.DESCRIPTION, "12 выступлений за год")
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.CURRENT_VALUE, 9)
                .set(SpeakerAchievements.SPEAKER_ACHIEVEMENTS.TARGET_VALUE, 12)
                .execute();

        dsl.insertInto(CfpRecommendations.CFP_RECOMMENDATIONS)
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.ID, UUID.fromString("55555555-5555-4555-8555-555555555555"))
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.TITLE, "Java Summit 2026")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.CITY, "Москва")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.INFLUENCE_LEVEL, "federal")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.TOPIC, "Java")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.DEADLINE, LocalDate.now().plusDays(40))
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.HOST_COMPANY, "Aurora Tech")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.URL, "https://example.org/cfp/java")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.ACTIVE, true)
                .execute();

        dsl.insertInto(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS)
                .set(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.SPEAKER_ID, 1L)
                .set(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.RECOMMENDATION_ID, UUID.fromString("55555555-5555-4555-8555-555555555555"))
                .set(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS.MATCH_SCORE, 93)
                .execute();

        dsl.insertInto(ParsingEvents.PARSING_EVENTS)
                .set(ParsingEvents.PARSING_EVENTS.ID, UUID.fromString("66666666-6666-4666-8666-666666666666"))
                .set(ParsingEvents.PARSING_EVENTS.CONFERENCE, "Java Summit")
                .set(ParsingEvents.PARSING_EVENTS.SPEAKER_NAME, "Alice Ivanova")
                .set(ParsingEvents.PARSING_EVENTS.COMPANY, "Aurora Tech")
                .set(ParsingEvents.PARSING_EVENTS.SOURCE_URL, "https://events.example.org/java")
                .set(ParsingEvents.PARSING_EVENTS.EVENT_DATE, LocalDate.now().minusDays(2))
                .set(ParsingEvents.PARSING_EVENTS.REGION, "Москва")
                .set(ParsingEvents.PARSING_EVENTS.STATUS, "confirmed")
                .set(ParsingEvents.PARSING_EVENTS.SPEAKER_ID, 1L)
                .set(ParsingEvents.PARSING_EVENTS.NOTE, "confirmed")
                .set(ParsingEvents.PARSING_EVENTS.DEDUP_KEY, "dedup-1")
                .set(ParsingEvents.PARSING_EVENTS.CREATED_AT, LocalDateTime.now().minusDays(2))
                .execute();
    }

    @Test
    @WithMockUser(username = "alice", roles = {"SPEAKER"})
    void generateSpeakerReport_usesCurrentUserAndBuildsRichPayload() {
        var report = reportsService.generateSpeakerReport(new ReportModels.SpeakerDashboardReportRequest(
                ReportModels.DashboardPeriod.YEAR,
                List.of("Москва"),
                List.of("backend-architect"),
                List.of("Aurora Tech"),
                ReportModels.DashboardInfluence.federal,
                80,
                100
        ));

        assertThat(report.speaker().id()).isEqualTo(1L);
        assertThat(report.speaker().name()).contains("Alice");
        assertThat(report.speaker().talksCount()).isEqualTo(2);
        assertThat(report.summary().previousNps()).isLessThanOrEqualTo(report.summary().nps());
        assertThat(report.tagCloud()).isNotEmpty();
        assertThat(report.filteredRecommendations()).hasSize(1);
    }

    @Test
    void generateDevRelReport_appliesRegionFilter() {
        var report = reportsService.generateDevRelReport(new ReportModels.DevRelDashboardReportRequest(
                ReportModels.DashboardPeriod.YEAR,
                List.of("Москва"),
                List.of(),
                List.of(),
                null,
                0,
                100,
                null,
                "week",
                null,
                null
        ));

        assertThat(report.rating().speakers()).hasSize(1);
        assertThat(report.rating().speakers().getFirst().region()).isEqualTo("Москва");
        assertThat(report.parsing().events()).hasSize(1);
        assertThat(report.parsing().events().getFirst().company()).isEqualTo("Aurora Tech");
        assertThat(report.summary().activeSpeakers()).isEqualTo(1);
    }

    @Test
    void getFilters_returnsDynamicValuesFromDatabase() {
        var filters = reportsService.getFilters();

        assertThat(filters.regions()).contains("Москва", "Казань");
        assertThat(filters.companies()).contains("Aurora Tech", "Delta Data");
        assertThat(filters.roles()).isNotEmpty();
        assertThat(filters.parsingSources()).isNotEmpty();
    }
}
