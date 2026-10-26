package ru.shaxowskiy.javaspeakerclub.feedback;

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.TalkFeedback;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalkFeedbackRecord;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerTestData;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ссылка на отзыв по выступлению, анонимная форма и отзывы в кабинете и каталоге.
 */
@SpringBootTest(properties = ProducerTestData.ISOLATED_H2_URL)
@ActiveProfiles("test")
@Transactional
class FeedbackFlowTest {

    private static final String ANSWERS = """
            {"answers":{"utility":0,"clarity":0,"examples":1,"pace":2,"engagement":0},"comment":"  Больше кейсов  "}
            """;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DSLContext dsl;

    private final ObjectMapper json = new ObjectMapper();
    private MockMvc mockMvc;
    private String speechId;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        ProducerTestData data = new ProducerTestData(dsl);
        data.clean();
        data.user(1L, "anna", "Анна", "Левина", "federal", null);
        data.user(2L, "timur", "Тимур", "Каримов", "local", null);
        data.user(3L, "dina", "Дина", "Релова", "local", null);
        data.role(1L, "SPEAKER");
        data.role(2L, "SPEAKER");
        data.role(3L, "DEVREL");

        String talkId = json.readTree(mockMvc.perform(post("/api/speaker-talks").with(user("anna").roles("SPEAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Надежные Java API\",\"topic\":\"Java\",\"description\":\"Про API\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        speechId = json.readTree(mockMvc.perform(post("/api/talks").with(user("anna").roles("SPEAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"speakerId":1,"topic":"Надежные Java API","talkDate":"%s","conferenceType":"MEETUP",
                                 "format":"OFFLINE","activityName":"Backend Meetup","activityDate":"%s","speakerTalkId":"%s"}
                                """.formatted(LocalDateTime.now().minusDays(3), LocalDateTime.now().minusDays(3), talkId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.speakerTalkId").value(talkId))
                .andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void listenerLeavesFeedbackByLink() throws Exception {
        String token = link("anna");
        assertThat(link("anna")).isEqualTo(token);

        mockMvc.perform(get("/api/public/feedback/" + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("Надежные Java API"))
                .andExpect(jsonPath("$.eventName").value("Backend Meetup"));
        mockMvc.perform(post("/api/public/feedback/" + token).contentType(MediaType.APPLICATION_JSON).content(ANSWERS))
                .andExpect(status().isCreated());

        TalkFeedbackRecord stored = dsl.selectFrom(TalkFeedback.TALK_FEEDBACK).fetchOne();
        assertThat(stored.getRating()).isEqualTo(7);
        assertThat(stored.getPositive()).isEqualTo("Очень полезно; Все понятно; Очень интересно");
        assertThat(stored.getNegative()).isEqualTo("Хотелось бы больше; Слишком медленный");
        assertThat(stored.getImprovementSuggestions()).isEqualTo("Больше кейсов");

        mockMvc.perform(get("/api/me/feedback").with(user("anna").roles("SPEAKER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].talkId").value(speechId))
                .andExpect(jsonPath("$[0].answers.examples").value(1))
                .andExpect(jsonPath("$[0].answers.pace").value(2))
                .andExpect(jsonPath("$[0].comment").value("Больше кейсов"));
        mockMvc.perform(get("/api/notifications").with(user("anna").roles("SPEAKER")))
                .andExpect(jsonPath("$[0].text").value("Новый отзыв слушателя"));
        mockMvc.perform(get("/api/me/feedback").with(user("timur").roles("SPEAKER")))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void incompleteAnswersAndUnknownTokenAreRejected() throws Exception {
        String token = link("anna");
        mockMvc.perform(post("/api/public/feedback/" + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"utility\":0,\"clarity\":3,\"examples\":1,\"pace\":0,\"engagement\":0}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Ответьте на все пять вопросов"));
        mockMvc.perform(get("/api/public/feedback/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Ссылка недействительна"));
    }

    @Test
    void onlyOwnerGetsFeedbackLink() throws Exception {
        mockMvc.perform(post("/api/talks/" + speechId + "/feedback-link").with(user("timur").roles("SPEAKER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void devrelSeesSpeakerInCatalog() throws Exception {
        mockMvc.perform(post("/api/public/feedback/" + link("anna")).contentType(MediaType.APPLICATION_JSON).content(ANSWERS))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/catalog/speakers").param("query", "анна").with(user("dina").roles("DEVREL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Анна Левина"))
                .andExpect(jsonPath("$[0].feedbackCount").value(1))
                .andExpect(jsonPath("$[0].speechCount").value(1));
        mockMvc.perform(get("/api/catalog/speakers/1").with(user("dina").roles("DEVREL")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speeches[0].event").value("Backend Meetup"))
                .andExpect(jsonPath("$.talks[0].name").value("Надежные Java API"))
                .andExpect(jsonPath("$.feedback[0].answers.utility").value(0));
        mockMvc.perform(get("/api/catalog/speakers/3").with(user("dina").roles("DEVREL")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/catalog/speakers").with(user("anna").roles("SPEAKER")))
                .andExpect(status().isForbidden());
    }

    private String link(String username) throws Exception {
        String body = mockMvc.perform(post("/api/talks/" + speechId + "/feedback-link").with(user(username).roles("SPEAKER")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("token").asText();
    }
}
