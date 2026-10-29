package ru.shaxowskiy.javaspeakerclub.producer.controller;

import com.jayway.jsonpath.JsonPath;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerTestData;
import ru.shaxowskiy.javaspeakerclub.producer.llm.ProducerLlmClient;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventAnalysisDraft;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventAnalysisDraft.DraftParam;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.RecommendationSummaryWriter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной сценарий подбора: организатор описал мероприятие → ИИ разобрал → подбор → приглашение → ответ спикера →
 * открытый CFP виден спикеру → метрики. Модель подменена: разбор описания отвечает, пересказ недоступен.
 */
@SpringBootTest(properties = ProducerTestData.ISOLATED_H2_URL)
@ActiveProfiles("test")
@Transactional
class ProducerMatchingFlowTest {

    private static final String DESCRIPTION = "Митап про Android-разработку в Москве для опытных разработчиков.";

    private static final RequestPostProcessor DEVREL = user("devrel").roles("DEVREL");
    private static final RequestPostProcessor OTHER_DEVREL = user("devrel2").roles("DEVREL");
    private static final RequestPostProcessor ILYA = user("ilya").roles("SPEAKER");
    private static final RequestPostProcessor ANNA = user("anna").roles("SPEAKER");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DSLContext dsl;

    @MockitoBean
    private ProducerLlmClient llmClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ProducerTestData data = new ProducerTestData(dsl);
        data.clean();
        data.user(1L, "anna", "Анна", "Левина", "federal", "8.8");
        data.user(2L, "ilya", "Илья", "Миронов", "regional", "7.9");
        data.user(3L, "devrel", "Дина", "Релова", "local", null);
        data.user(4L, "devrel2", "Олег", "Другой", "local", null);
        data.role(1L, "SPEAKER");
        data.role(2L, "SPEAKER");
        data.role(3L, "DEVREL");
        data.role(4L, "DEVREL");

        LocalDateTime now = LocalDateTime.now();
        UUID compose = data.talk(2L, "Jetpack Compose в продакшене", now.minusDays(20));
        data.tag(compose, "android");
        UUID memory = data.talk(2L, "Утечки памяти на Android", now.minusDays(60));
        data.tag(memory, "android");
        data.feedback(compose, 10, "Живое демо", "-", now.minusDays(19));
        UUID metrics = data.talk(1L, "Продуктовые метрики", now.minusDays(30));
        data.tag(metrics, "product");

        when(llmClient.modelName()).thenReturn("test-model");
        when(llmClient.generate(anyString(), anyString(), eq(EventAnalysisDraft.class), any())).thenReturn(new EventAnalysisDraft(Map.of(
                "topics", new DraftParam(List.of("Android-разработка"), "stated", "Митап про Android-разработку"),
                "city", new DraftParam(List.of("Москва"), "stated", "в Москве"),
                "audienceLevel", new DraftParam(List.of("senior"), "inferred", "для опытных разработчиков")
        ), List.of("android"), List.of()));
        when(llmClient.generate(anyString(), anyString(), eq(RecommendationSummaryWriter.Draft.class), any()))
                .thenThrow(new IllegalStateException("model is down"));
    }

    @Test
    void organizerFindsSpeakerInvitesAndSpeakerAnswers() throws Exception {
        String created = mockMvc.perform(post("/api/producer/events").with(DEVREL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Android Meetup\",\"description\":\"" + DESCRIPTION + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.params[0].key").value("topics"))
                .andExpect(jsonPath("$.params[?(@.key=='audienceLevel')].status").value(hasItem("inferred")))
                .andExpect(jsonPath("$.questions[*].key").value(hasItem("attendance")))
                .andReturn().getResponse().getContentAsString();
        String eventId = JsonPath.read(created, "$.id");

        mockMvc.perform(patch("/api/producer/events/" + eventId).with(DEVREL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"attendance\":\"только офлайн\"},\"skip\":[\"talkType\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[?(@.key=='attendance')].status").value(hasItem("answered")))
                .andExpect(jsonPath("$.questions[?(@.key=='talkType')].status").value(hasItem("skipped")));

        mockMvc.perform(post("/api/producer/events/" + eventId + "/match").with(DEVREL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].items[0].name").value("Илья Миронов"))
                .andExpect(jsonPath("$.groups[0].items[0].whyFits").isNotEmpty())
                .andExpect(jsonPath("$.groups[0].items[0].confidence.level").exists())
                .andExpect(jsonPath("$.runId").exists());

        mockMvc.perform(post("/api/producer/events/" + eventId + "/candidates/2/actions").with(DEVREL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"invited\",\"note\":\"Ждём с докладом про Compose\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invitationStatus").value("Отправлено"));

        mockMvc.perform(get("/api/producer/me/invitations").with(ILYA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("sent"))
                .andExpect(jsonPath("$[0].whyYou").isNotEmpty())
                .andExpect(jsonPath("$[0].note").value("Ждём с докладом про Compose"));

        mockMvc.perform(post("/api/producer/me/invitations/" + eventId + "/response").with(ILYA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"accepted\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"));

        mockMvc.perform(post("/api/producer/events/" + eventId + "/candidates/2/actions").with(DEVREL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"talk_given\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/producer/events/" + eventId + "/history").with(DEVREL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].kind").value(hasItem("ai")))
                .andExpect(jsonPath("$[*].action").value(hasItem("invitation_accepted")));

        mockMvc.perform(get("/api/producer/metrics").with(DEVREL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizerFunnel[0].count").value(1))
                .andExpect(jsonPath("$.organizerFunnel[3].count").value(1));
    }

    @Test
    void openedCfpOfPlatformEventReachesSpeakerRecommendations() throws Exception {
        String created = mockMvc.perform(post("/api/producer/events").with(DEVREL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Android Meetup\",\"description\":\"" + DESCRIPTION + "\"}"))
                .andReturn().getResponse().getContentAsString();
        String eventId = JsonPath.read(created, "$.id");

        mockMvc.perform(post("/api/producer/events/" + eventId + "/cfp").with(DEVREL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deadline\":\"" + LocalDate.now().plusDays(20) + "\",\"level\":\"regional\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cfpId").exists());

        mockMvc.perform(post("/api/producer/me/events").with(ILYA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[*].items[*].eventId").value(hasItem(eventId)));
    }

    @Test
    void organizerSeesOnlyOwnEventsAndSpeakerCannotCreateThem() throws Exception {
        String created = mockMvc.perform(post("/api/producer/events").with(DEVREL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{\"topics\":[\"Android\"]}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String eventId = JsonPath.read(created, "$.id");

        mockMvc.perform(get("/api/producer/events/" + eventId).with(OTHER_DEVREL))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/producer/events/not-a-uuid").with(DEVREL))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/producer/no-such-endpoint").with(DEVREL))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/producer/events").with(ANNA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"params\":{\"topics\":[\"Android\"]}}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/producer/me/invitations/" + eventId + "/response").with(ANNA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"response\":\"accepted\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/producer/metrics").with(ANNA))
                .andExpect(status().isForbidden());
    }

    @Test
    void cardIsBuiltFromFormWhenDescriptionCannotBeAnalyzed() throws Exception {
        when(llmClient.generate(anyString(), anyString(), eq(EventAnalysisDraft.class), any()))
                .thenThrow(new IllegalStateException("model is down"));

        mockMvc.perform(post("/api/producer/events").with(DEVREL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"" + DESCRIPTION + "\",\"params\":{\"topics\":[\"Android\"]}}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.params[0].status").value("confirmed"))
                .andExpect(jsonPath("$.notes[0]").value(org.hamcrest.Matchers.containsString("не удалось разобрать")));
    }
}
