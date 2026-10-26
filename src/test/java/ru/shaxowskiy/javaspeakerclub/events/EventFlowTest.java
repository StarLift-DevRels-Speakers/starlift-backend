package ru.shaxowskiy.javaspeakerclub.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerTestData;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Сквозной сценарий: DevRel публикует мероприятие, спикер подаёт заявку, DevRel решает, приглашения и уведомления.
 */
@SpringBootTest(properties = ProducerTestData.ISOLATED_H2_URL)
@ActiveProfiles("test")
@Transactional
class EventFlowTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DSLContext dsl;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        ProducerTestData data = new ProducerTestData(dsl);
        data.clean();
        data.user(1L, "anna", "Анна", "Левина", "federal", null);
        data.user(2L, "dina", "Дина", "Релова", "local", null);
        data.user(3L, "timur", "Тимур", "Каримов", "local", null);
        data.role(1L, "SPEAKER");
        data.role(2L, "DEVREL");
        data.role(3L, "SPEAKER");
    }

    @Test
    void speakerAppliesAndDevrelAccepts() throws Exception {
        String eventId = publishedEvent(LocalDate.now().plusDays(30), LocalDate.now().plusDays(10));
        String talkId = talk("anna", "Надежные Java API");

        mockMvc.perform(as(get("/api/events"), "anna", "SPEAKER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(eventId))
                .andExpect(jsonPath("$[0].own").value(false))
                .andExpect(jsonPath("$[0].ownerName").value("Дина Релова"));

        apply(eventId, talkId).andExpect(status().isCreated());
        apply(eventId, talkId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Заявка с этим докладом уже отправлена"));

        JsonNode incoming = read(as(get("/api/applications"), "dina", "DEVREL"));
        assertThat(incoming).hasSize(1);
        assertThat(incoming.get(0).get("speakerName").asText()).isEqualTo("Анна Левина");
        assertThat(incoming.get(0).get("topic").asText()).isEqualTo("Надежные Java API");
        String applicationId = incoming.get(0).get("id").asText();

        mockMvc.perform(as(post("/api/applications/" + applicationId + "/decision"), "anna", "SPEAKER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"accepted\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(post("/api/applications/" + applicationId + "/decision"), "dina", "DEVREL")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"accepted\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(get("/api/applications"), "anna", "SPEAKER"))
                .andExpect(jsonPath("$[0].status").value("accepted"));
        mockMvc.perform(as(get("/api/notifications"), "anna", "SPEAKER"))
                .andExpect(jsonPath("$[0].text").value("Заявка на «Backend Meetup»: принята"))
                .andExpect(jsonPath("$[0].read").value(false));
        mockMvc.perform(as(get("/api/notifications"), "dina", "DEVREL"))
                .andExpect(jsonPath("$[?(@.text == 'Поступила заявка на Backend Meetup')]").exists());
    }

    @Test
    void talkChangeNeedsDevrelApproval() throws Exception {
        String eventId = publishedEvent(LocalDate.now().plusDays(30), LocalDate.now().plusDays(10));
        String first = talk("anna", "Первый доклад");
        String second = talk("anna", "Второй доклад");
        apply(eventId, first).andExpect(status().isCreated());
        String applicationId = read(as(get("/api/applications"), "anna", "SPEAKER")).get(0).get("id").asText();

        mockMvc.perform(as(post("/api/applications/" + applicationId + "/talk-change"), "anna", "SPEAKER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"talkId\":\"" + second + "\"}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(get("/api/applications"), "anna", "SPEAKER"))
                .andExpect(jsonPath("$[0].talkChange.topic").value("Второй доклад"));

        mockMvc.perform(as(post("/api/applications/" + applicationId + "/decision"), "dina", "DEVREL")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"accepted\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Сначала ответьте на запрос смены доклада"));

        mockMvc.perform(as(post("/api/applications/" + applicationId + "/talk-change/answer"), "dina", "DEVREL")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"approved\":true}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(get("/api/applications"), "anna", "SPEAKER"))
                .andExpect(jsonPath("$[0].topic").value("Второй доклад"))
                .andExpect(jsonPath("$[0].talkId").value(second))
                .andExpect(jsonPath("$[0].talkChange").isEmpty());
    }

    @Test
    void closedCfpRejectsApplication() throws Exception {
        String eventId = publishedEvent(LocalDate.now().plusDays(30), null);
        apply(eventId, talk("anna", "Доклад"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Прием заявок завершен"));
    }

    @Test
    void draftIsVisibleOnlyToOwner() throws Exception {
        createEvent(LocalDate.now().plusDays(30), LocalDate.now().plusDays(10));

        mockMvc.perform(as(get("/api/events"), "dina", "DEVREL"))
                .andExpect(jsonPath("$[0].state").value("draft"))
                .andExpect(jsonPath("$[0].own").value(true));
        mockMvc.perform(as(get("/api/events"), "anna", "SPEAKER"))
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(as(post("/api/events"), "anna", "SPEAKER")
                        .contentType(MediaType.APPLICATION_JSON).content(eventJson(LocalDate.now().plusDays(5), null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void eventValidationRejectsPastDateAndLateCfp() throws Exception {
        mockMvc.perform(as(post("/api/events"), "dina", "DEVREL")
                        .contentType(MediaType.APPLICATION_JSON).content(eventJson(LocalDate.now().minusDays(1), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Дата мероприятия не может быть в прошлом"));
        mockMvc.perform(as(post("/api/events"), "dina", "DEVREL")
                        .contentType(MediaType.APPLICATION_JSON).content(eventJson(LocalDate.now().plusDays(5), LocalDate.now().plusDays(6))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Дедлайн CFP не может быть позже даты мероприятия"));
    }

    @Test
    void invitationIsAnsweredBySpeaker() throws Exception {
        String eventId = publishedEvent(LocalDate.now().plusDays(30), LocalDate.now().plusDays(10));

        invite(eventId, 1L).andExpect(status().isCreated());
        invite(eventId, 1L)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Приглашение на это мероприятие уже ожидает ответа Спикера"));
        invite(eventId, 2L).andExpect(status().isNotFound());

        apply(eventId, talk("anna", "Доклад"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("У вас есть приглашение на это мероприятие — ответьте на него"));

        JsonNode received = read(as(get("/api/invitations"), "anna", "SPEAKER"));
        assertThat(received).hasSize(1);
        String invitationId = received.get(0).get("id").asText();
        assertThat(read(as(get("/api/invitations"), "timur", "SPEAKER"))).isEmpty();

        mockMvc.perform(as(post("/api/invitations/" + invitationId + "/answer"), "timur", "SPEAKER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"accepted\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(post("/api/invitations/" + invitationId + "/answer"), "anna", "SPEAKER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"accepted\"}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(as(get("/api/invitations"), "dina", "DEVREL"))
                .andExpect(jsonPath("$[0].status").value("accepted"))
                .andExpect(jsonPath("$[0].speakerName").value("Анна Левина"));
        mockMvc.perform(as(get("/api/notifications"), "dina", "DEVREL"))
                .andExpect(jsonPath("$[0].text").value("Анна Левина: приглашение на «Backend Meetup» принято"));

        mockMvc.perform(as(post("/api/notifications/read"), "dina", "DEVREL")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        mockMvc.perform(as(get("/api/notifications"), "dina", "DEVREL"))
                .andExpect(jsonPath("$[?(@.read == false)]").isEmpty());
    }

    private String publishedEvent(LocalDate date, LocalDate cfp) throws Exception {
        String id = createEvent(date, cfp);
        mockMvc.perform(as(post("/api/events/" + id + "/publish"), "dina", "DEVREL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("published"));
        mockMvc.perform(as(post("/api/events/" + id + "/publish"), "dina", "DEVREL"))
                .andExpect(status().isConflict());
        return id;
    }

    private String createEvent(LocalDate date, LocalDate cfp) throws Exception {
        String body = mockMvc.perform(as(post("/api/events"), "dina", "DEVREL")
                        .contentType(MediaType.APPLICATION_JSON).content(eventJson(date, cfp)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private static String eventJson(LocalDate date, LocalDate cfp) {
        return """
                {"name":"Backend Meetup","date":"%s","cfp":%s,"city":"Москва","format":"OFFLINE",
                 "topic":"Backend","audience":"Senior","description":"Доклады"}
                """.formatted(date, cfp == null ? "null" : "\"" + cfp + "\"");
    }

    private String talk(String username, String name) throws Exception {
        String body = mockMvc.perform(as(post("/api/speaker-talks"), username, "SPEAKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"topic\":\"Java\",\"description\":\"\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions apply(String eventId, String talkId) throws Exception {
        return mockMvc.perform(as(post("/api/applications"), "anna", "SPEAKER")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + eventId + "\",\"talkId\":\"" + talkId + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions invite(String eventId, long speakerId) throws Exception {
        return mockMvc.perform(as(post("/api/invitations"), "dina", "DEVREL")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + eventId + "\",\"speakerId\":" + speakerId + ",\"topic\":\"Kafka\"}"));
    }

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String username, String role) {
        return request.with(user(username).roles(role));
    }
}
