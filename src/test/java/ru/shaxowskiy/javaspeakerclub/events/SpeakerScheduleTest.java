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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Предстоящие выступления спикера: напоминания по этапам и выгрузка в календарь.
 */
@SpringBootTest(properties = ProducerTestData.ISOLATED_H2_URL)
@ActiveProfiles("test")
@Transactional
class SpeakerScheduleTest {

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
        data.role(1L, "SPEAKER");
        data.role(2L, "DEVREL");
    }

    @Test
    void acceptedApplicationAndInvitationBecomeScheduleWithReminders() throws Exception {
        String applied = publishedEvent("Java Day", LocalDate.now().plusDays(3), LocalDate.now().plusDays(1));
        String invited = publishedEvent("Kafka Conf", LocalDate.now().plusDays(10), null);
        String pending = publishedEvent("Pending Meetup", LocalDate.now().plusDays(1), LocalDate.now());

        apply(applied, talk("Надёжные Java API"));
        apply(pending, talk("Ещё не принят"));
        for (JsonNode application : read(as(get("/api/applications"), "dina", "DEVREL"))) {
            if (application.get("eventId").asText().equals(applied)) {
                decide(application.get("id").asText(), "accepted");
            }
        }
        assertThat(read(as(get("/api/me/schedule"), "anna", "SPEAKER"))).hasSize(1);

        mockMvc.perform(as(post("/api/invitations"), "dina", "DEVREL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + invited + "\",\"speakerId\":1,\"topic\":\"Kafka в проде\"}"))
                .andExpect(status().isCreated());
        String invitationId = read(as(get("/api/invitations"), "anna", "SPEAKER")).get(0).get("id").asText();
        mockMvc.perform(as(post("/api/invitations/" + invitationId + "/answer"), "anna", "SPEAKER")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"accepted\"}"))
                .andExpect(status().isNoContent());

        JsonNode schedule = read(as(get("/api/me/schedule"), "anna", "SPEAKER"));
        assertThat(schedule).hasSize(2);
        assertThat(schedule.get(0).get("eventId").asText()).isEqualTo(applied);
        assertThat(schedule.get(0).get("topic").asText()).isEqualTo("Надёжные Java API");
        assertThat(schedule.get(0).get("daysLeft").asLong()).isEqualTo(3);
        assertThat(schedule.get(0).get("reminder").asText()).isEqualTo("THREE_DAYS");
        assertThat(schedule.get(0).get("ownerName").asText()).isEqualTo("Дина Релова");
        assertThat(schedule.get(0).get("googleCalendarUrl").asText())
                .startsWith("https://calendar.google.com/calendar/render?action=TEMPLATE")
                .contains("&dates=" + LocalDate.now().plusDays(3).format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
        assertThat(schedule.get(1).get("eventId").asText()).isEqualTo(invited);
        assertThat(schedule.get(1).get("topic").asText()).isEqualTo("Kafka в проде");
        assertThat(schedule.get(1).get("reminder").isNull()).isTrue();

        assertThat(schedule.get(0).get("kind").asText()).isEqualTo("SPEECH");

        // DevRel видит свои опубликованные мероприятия, черновик — нет.
        mockMvc.perform(as(post("/api/events"), "dina", "DEVREL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Черновик\",\"date\":\"" + LocalDate.now().plusDays(2)
                                + "\",\"cfp\":null,\"city\":\"Москва\",\"format\":\"OFFLINE\",\"topic\":\"Backend\"}"))
                .andExpect(status().isCreated());
        JsonNode hosting = read(as(get("/api/me/schedule"), "dina", "DEVREL"));
        assertThat(hosting).hasSize(3);
        assertThat(hosting.get(0).get("eventId").asText()).isEqualTo(pending);
        assertThat(hosting.get(0).get("kind").asText()).isEqualTo("HOSTING");
        assertThat(hosting.get(0).get("reminder").asText()).isEqualTo("ONE_DAY");
        assertThat(hosting.get(0).get("topic").asText()).isEqualTo("Backend");

        // Мероприятие, где спикер не выступает, ему не выгружается.
        mockMvc.perform(as(get("/api/me/schedule/" + pending + "/calendar.ics"), "anna", "SPEAKER"))
                .andExpect(status().isNotFound());

        String ics = mockMvc.perform(as(get("/api/me/schedule/calendar.ics"), "anna", "SPEAKER"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.startsWith("attachment; filename=\"starlift-")))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(ics).contains("UID:" + applied + "@starlift", "UID:" + invited + "@starlift", "TRIGGER:-P3D");

        String single = mockMvc.perform(as(get("/api/me/schedule/" + invited + "/calendar.ics"), "anna", "SPEAKER"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(single).contains("UID:" + invited + "@starlift").doesNotContain(applied);
    }

    private String publishedEvent(String name, LocalDate date, LocalDate cfp) throws Exception {
        String body = mockMvc.perform(as(post("/api/events"), "dina", "DEVREL")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","date":"%s","cfp":%s,"city":"Москва","format":"OFFLINE",
                                 "topic":"Backend","audience":"Senior","description":"Доклады"}
                                """.formatted(name, date, cfp == null ? "null" : "\"" + cfp + "\"")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        mockMvc.perform(as(post("/api/events/" + id + "/publish"), "dina", "DEVREL")).andExpect(status().isOk());
        return id;
    }

    private String talk(String name) throws Exception {
        String body = mockMvc.perform(as(post("/api/speaker-talks"), "anna", "SPEAKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"topic\":\"Java\",\"description\":\"\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asText();
    }

    private void apply(String eventId, String talkId) throws Exception {
        mockMvc.perform(as(post("/api/applications"), "anna", "SPEAKER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventId\":\"" + eventId + "\",\"talkId\":\"" + talkId + "\"}"))
                .andExpect(status().isCreated());
    }

    private void decide(String applicationId, String status) throws Exception {
        mockMvc.perform(as(post("/api/applications/" + applicationId + "/decision"), "dina", "DEVREL")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isNoContent());
    }

    private JsonNode read(MockHttpServletRequestBuilder request) throws Exception {
        return json.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, String username, String role) {
        return request.with(user(username).roles(role));
    }
}
