package ru.shaxowskiy.javaspeakerclub.signals.controller;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerTestData;
import ru.shaxowskiy.javaspeakerclub.signals.client.WeakSignalsClient;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentScoreResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = ProducerTestData.ISOLATED_H2_URL)
@ActiveProfiles("test")
@Transactional
class WeakSignalsControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DSLContext dsl;

    @MockitoBean
    private WeakSignalsClient client;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ProducerTestData data = new ProducerTestData(dsl);
        data.clean();
        data.user(1L, "anna", "Анна", "Левина", "federal", "8.8");
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/signals/speakers/1/talent-score"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "anna", roles = "SPEAKER")
    void speakerIsForbidden() throws Exception {
        mockMvc.perform(post("/api/signals/speakers/1/talent-score"))
                .andExpect(status().isForbidden());
        verify(client, never()).score(any());
    }

    @Test
    @WithMockUser(username = "devrel", roles = "DEVREL")
    void devrelScoresSpeaker() throws Exception {
        when(client.score(argThat(request -> request.id() == 1L)))
                .thenReturn(new TalentScoreResponse(1L, 4, "Растёт", "medium"));

        mockMvc.perform(post("/api/signals/speakers/1/talent-score"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.score").value(4))
                .andExpect(jsonPath("$.confidence").value("medium"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminPreviewsRequestInSnakeCase() throws Exception {
        mockMvc.perform(get("/api/signals/speakers/1/talent-request"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("anna"))
                .andExpect(jsonPath("$.influence").value("federal"))
                .andExpect(jsonPath("$.talks_total").value(0))
                .andExpect(jsonPath("$.avg_rating").isNumber())
                .andExpect(jsonPath("$.achievements_progress_pct").isNumber())
                .andExpect(jsonPath("$.region").isEmpty());
        verify(client, never()).score(any());
    }

    @Test
    @WithMockUser(username = "devrel", roles = "DEVREL")
    void unknownSpeakerIsNotFound() throws Exception {
        mockMvc.perform(post("/api/signals/speakers/999/talent-score"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "devrel", roles = "DEVREL")
    void upstreamFailureIsPassedAsBadGateway() throws Exception {
        when(client.score(any())).thenThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Сервис оценки спикеров недоступен"));

        mockMvc.perform(post("/api/signals/speakers/1/talent-score"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Сервис оценки спикеров недоступен"));
    }
}
