package ru.shaxowskiy.javaspeakerclub.producer.controller;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerTestData;
import ru.shaxowskiy.javaspeakerclub.producer.llm.ProducerLlmClient;
import ru.shaxowskiy.javaspeakerclub.producer.service.ProducerService;
import ru.shaxowskiy.javaspeakerclub.repository.ProducerPlanRepository;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = ProducerTestData.ISOLATED_H2_URL)
@ActiveProfiles("test")
@Transactional
class ProducerControllerSecurityTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private ProducerPlanRepository planRepository;

    @MockitoBean
    private ProducerLlmClient llmClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // В Boot 4 без spring-boot-starter-security-test @AutoConfigureMockMvc не подключает @WithMockUser.
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        ProducerTestData data = new ProducerTestData(dsl);
        data.clean();
        data.user(1L, "anna", "Анна", "Левина", "federal", "8.8");
        data.user(2L, "timur", "Тимур", "Каримов", "local", null);
        data.user(3L, "devrel", "Дина", "Релова", "local", null);
        data.role(1L, "SPEAKER");
        data.role(2L, "SPEAKER");
        data.role(3L, "DEVREL");

        planRepository.insert(new ProducerPlanRepository.NewPlan(
                1L, ProducerService.STATUS_INSUFFICIENT_DATA, null, null, null, null, "producer-v1", null));
    }

    @Test
    void anonymousCannotReadPlan() throws Exception {
        mockMvc.perform(get("/api/producer/me/plan"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "anna", roles = "SPEAKER")
    void speakerReadsOwnPlan() throws Exception {
        mockMvc.perform(get("/api/producer/me/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakerId").value(1))
                .andExpect(jsonPath("$.status").value("insufficient_data"));
    }

    @Test
    @WithMockUser(username = "anna", roles = "SPEAKER")
    void speakerCannotReadOtherSpeakersPlan() throws Exception {
        mockMvc.perform(get("/api/producer/speakers/2/plan"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "devrel", roles = "DEVREL")
    void devrelReadsAnySpeakersPlan() throws Exception {
        mockMvc.perform(get("/api/producer/speakers/1/plan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakerId").value(1));
    }

    @Test
    @WithMockUser(username = "anna", roles = "SPEAKER")
    void speakerCannotOpenReportOrRegenerateAll() throws Exception {
        mockMvc.perform(get("/api/producer/report"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/producer/plans/regenerate-all"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "devrel", roles = "DEVREL")
    void devrelOpensHtmlReport() throws Exception {
        mockMvc.perform(get("/api/producer/report"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Отчёт продюсера")))
                .andExpect(content().string(containsString("Анна Левина")));
    }
}
