package ru.shaxowskiy.javaspeakerclub.gamification.controller;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.shaxowskiy.javaspeakerclub.gamification.GamificationTestData;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = GamificationTestData.ISOLATED_H2_URL)
@ActiveProfiles("test")
@Transactional
class GamificationControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DSLContext dsl;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        GamificationTestData data = new GamificationTestData(dsl);
        data.clean();
        data.user(1L, "anna", "SPEAKER");
        data.user(2L, "timur", "SPEAKER");
        data.user(3L, "dina", "DEVREL");
        data.user(4L, "ivan", "USER");
    }

    @Test
    void anonymousCannotReadLeaderboard() throws Exception {
        mockMvc.perform(get("/api/gamification/leaderboard/league"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ivan", roles = "USER")
    void userReadsLeaderboardButDoesNotParticipate() throws Exception {
        mockMvc.perform(get("/api/gamification/leaderboard/league"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.league").value("BRONZE"))
                .andExpect(jsonPath("$.entries.length()").value(2));
        mockMvc.perform(get("/api/gamification/speakers/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.league").value("BRONZE"));
        mockMvc.perform(get("/api/gamification/me"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/gamification/me/check-in"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "anna", roles = "SPEAKER")
    void speakerChecksInAndSeesOwnProfile() throws Exception {
        mockMvc.perform(post("/api/gamification/me/check-in"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyCheckedIn").value(false))
                .andExpect(jsonPath("$.awardedPoints").value(5));
        mockMvc.perform(get("/api/gamification/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakerId").value(1))
                .andExpect(jsonPath("$.weeklyPoints").value(5))
                .andExpect(jsonPath("$.checkedInToday").value(true));
        mockMvc.perform(get("/api/gamification/me/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reason").value("DAILY_CHECK_IN"));
    }

    @Test
    @WithMockUser(username = "anna", roles = "SPEAKER")
    void speakerCannotAwardPointsReadOthersHistoryOrCloseSeason() throws Exception {
        mockMvc.perform(post("/api/gamification/speakers/1/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\": 100, \"comment\": \"self\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/gamification/speakers/2/points"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/gamification/seasons/current/close"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "dina", roles = "DEVREL")
    void devrelAwardsPointsAndReadsHistory() throws Exception {
        mockMvc.perform(post("/api/gamification/speakers/2/points")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\": 100, \"comment\": \"Митап сообщества\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reason").value("MANUAL"))
                .andExpect(jsonPath("$.awardedBy").value(3));
        mockMvc.perform(get("/api/gamification/speakers/2/points"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].points").value(100));
        mockMvc.perform(get("/api/gamification/leaderboard/global").param("period", "WEEK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].speakerId").value(2))
                .andExpect(jsonPath("$.season.status").value("ACTIVE"));
        mockMvc.perform(get("/api/gamification/leaderboard/global").param("limit", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "dina", roles = "DEVREL")
    void devrelClosesSeason() throws Exception {
        mockMvc.perform(post("/api/gamification/seasons/current/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedSeason.status").value("CLOSED"))
                .andExpect(jsonPath("$.nextSeason.status").value("ACTIVE"))
                .andExpect(jsonPath("$.participants").value(2));
        mockMvc.perform(get("/api/gamification/seasons"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    @WithMockUser(username = "ivan", roles = "USER")
    void catalogsAreAvailable() throws Exception {
        mockMvc.perform(get("/api/gamification/leagues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].participants").value(2))
                .andExpect(jsonPath("$[4].promotionCount").value(0));
        mockMvc.perform(get("/api/gamification/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("FIRST_TALK"));
    }
}
