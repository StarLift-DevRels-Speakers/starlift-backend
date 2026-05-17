package ru.shaxowskiy.javaspeakerclub.service;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.DashboardInfluenceDto;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.SpeakerAchievementUpsertRequest;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.SpeakerCfpLinkUpsertRequest;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.SpeakerDashboardProfilePatchRequest;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.CfpRecommendations;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerCfpRecommendations;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerNpsHistory;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.SpeakerRoles;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.Users;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SpeakerDashboardCommandServiceTest {

    @Autowired
    private SpeakerDashboardCommandService commandService;

    @Autowired
    private RoleService roleService;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void setUp() {
        dsl.deleteFrom(SpeakerCfpRecommendations.SPEAKER_CFP_RECOMMENDATIONS).execute();
        dsl.deleteFrom(SpeakerNpsHistory.SPEAKER_NPS_HISTORY).execute();
        dsl.deleteFrom(SpeakerRoles.SPEAKER_ROLES).execute();
        dsl.deleteFrom(CfpRecommendations.CFP_RECOMMENDATIONS).execute();
        dsl.deleteFrom(Users.USERS).execute();

        dsl.insertInto(Users.USERS)
                .set(Users.USERS.ID, 1L)
                .set(Users.USERS.USERNAME, "alice")
                .set(Users.USERS.PASSWORD, "pwd")
                .set(Users.USERS.NPS, new BigDecimal("8.1"))
                .set(Users.USERS.REGION, "Москва")
                .set(Users.USERS.COMPANY, "Aurora")
                .set(Users.USERS.INFLUENCE, "federal")
                .set(Users.USERS.PROFILE_COMPLETION, 70)
                .set(Users.USERS.AUDIENCE, 350)
                .set(Users.USERS.TOP_CONFERENCE_CANDIDATE, false)
                .set(Users.USERS.AVATAR_COLOR, "#0ea5e9")
                .execute();
        dsl.insertInto(Users.USERS)
                .set(Users.USERS.ID, 2L)
                .set(Users.USERS.USERNAME, "bob")
                .set(Users.USERS.PASSWORD, "pwd")
                .set(Users.USERS.NPS, new BigDecimal("7.2"))
                .set(Users.USERS.REGION, "Казань")
                .set(Users.USERS.COMPANY, "Delta")
                .set(Users.USERS.INFLUENCE, "regional")
                .set(Users.USERS.PROFILE_COMPLETION, 60)
                .set(Users.USERS.AUDIENCE, 180)
                .set(Users.USERS.TOP_CONFERENCE_CANDIDATE, false)
                .set(Users.USERS.AVATAR_COLOR, "#22c55e")
                .execute();

        dsl.insertInto(SpeakerRoles.SPEAKER_ROLES)
                .set(SpeakerRoles.SPEAKER_ROLES.NAME, "Backend")
                .set(SpeakerRoles.SPEAKER_ROLES.SPEAKER_ID, 1L)
                .execute();

        dsl.insertInto(CfpRecommendations.CFP_RECOMMENDATIONS)
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.ID, UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"))
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.TITLE, "Java Summit")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.CITY, "Москва")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.INFLUENCE_LEVEL, "federal")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.TOPIC, "Java")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.DEADLINE, LocalDate.now().plusDays(30))
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.HOST_COMPANY, "Aurora")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.URL, "https://example.org/cfp/java")
                .set(CfpRecommendations.CFP_RECOMMENDATIONS.ACTIVE, true)
                .execute();
    }

    @Test
    @WithMockUser(username = "alice", roles = {"SPEAKER"})
    void speakerCanPatchOwnProfile() {
        var result = commandService.patchDashboardProfile(1L, new SpeakerDashboardProfilePatchRequest(
                "Санкт-Петербург",
                "Aurora",
                DashboardInfluenceDto.federal,
                true,
                "#123456",
                LocalDateTime.now()
        ));

        assertThat(result.speakerId()).isEqualTo(1L);
        assertThat(result.region()).isEqualTo("Санкт-Петербург");
        assertThat(result.topConferenceCandidate()).isTrue();
    }

    @Test
    @WithMockUser(username = "alice", roles = {"SPEAKER"})
    void speakerCannotPatchAnotherSpeaker() {
        assertThatThrownBy(() -> commandService.patchDashboardProfile(2L, new SpeakerDashboardProfilePatchRequest(
                "Новосибирск", null, null, null, null, null
        )))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "devrel", roles = {"DEVREL"})
    void devrelCanPatchAnySpeaker() {
        var result = commandService.patchDashboardProfile(2L, new SpeakerDashboardProfilePatchRequest(
                null,
                "New Company",
                DashboardInfluenceDto.international,
                null,
                null,
                null
        ));

        assertThat(result.speakerId()).isEqualTo(2L);
        assertThat(result.company()).isEqualTo("New Company");
        assertThat(result.influence()).isEqualTo(DashboardInfluenceDto.international);
    }

    @Test
    @WithMockUser(username = "devrel", roles = {"DEVREL"})
    void putNpsCreatesNpsHistoryRow() {
        int before = dsl.fetchCount(SpeakerNpsHistory.SPEAKER_NPS_HISTORY);

        roleService.updateNps(1L, new BigDecimal("9.0"));

        int after = dsl.fetchCount(SpeakerNpsHistory.SPEAKER_NPS_HISTORY);
        assertThat(after).isEqualTo(before + 1);
    }

    @Test
    @WithMockUser(username = "alice", roles = {"SPEAKER"})
    void speakerCanReplaceAchievementsAndCfpLinks() {
        var achievements = commandService.replaceAchievements(1L, List.of(
                new SpeakerAchievementUpsertRequest(null, "Публичные выступления", "10 выступлений", 5, 10)
        ));
        assertThat(achievements).hasSize(1);
        assertThat(achievements.getFirst().speakerId()).isEqualTo(1L);

        var links = commandService.replaceSpeakerCfpLinks(1L, List.of(
                new SpeakerCfpLinkUpsertRequest("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa", 92)
        ));
        assertThat(links).hasSize(1);
        assertThat(links.getFirst().speakerId()).isEqualTo(1L);
        assertThat(links.getFirst().matchScore()).isEqualTo(92);
    }
}
