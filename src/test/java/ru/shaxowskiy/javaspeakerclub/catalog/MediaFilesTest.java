package ru.shaxowskiy.javaspeakerclub.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerTestData;
import ru.shaxowskiy.javaspeakerclub.service.MinioService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Медиафайлы спикера: загрузка с привязкой к заготовке доклада, список и удаление. MinIO замокан.
 */
@SpringBootTest(properties = ProducerTestData.ISOLATED_H2_URL)
@ActiveProfiles("test")
@Transactional
class MediaFilesTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private DSLContext dsl;

    @MockitoBean
    private MinioService minioService;

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
        data.user(2L, "timur", "Тимур", "Каримов", "local", null);
        data.role(1L, "SPEAKER");
        data.role(2L, "SPEAKER");
        when(minioService.uploadFile(eq("user-media/1"), any())).thenReturn("user-media/1/uuid_slides.pdf");
    }

    @Test
    void speakerUploadsListsAndDeletesFile() throws Exception {
        String talkId = json.readTree(mockMvc.perform(post("/api/speaker-talks").with(user("anna").roles("SPEAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Доклад\",\"topic\":\"Java\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        MockMultipartFile file = new MockMultipartFile("file", "slides.pdf", "application/pdf", new byte[]{1, 2, 3});
        String id = json.readTree(mockMvc.perform(multipart("/api/media/upload").file(file).param("talkId", talkId)
                        .with(user("anna").roles("SPEAKER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("slides.pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(3))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/media/files").with(user("anna").roles("SPEAKER")))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].talkId").value(talkId));
        mockMvc.perform(get("/api/media/files").with(user("timur").roles("SPEAKER")))
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(delete("/api/media/files/" + id).with(user("timur").roles("SPEAKER")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/media/files/" + id).with(user("anna").roles("SPEAKER")))
                .andExpect(status().isNoContent());
        verify(minioService).deleteFile("user-media/1/uuid_slides.pdf");
        mockMvc.perform(get("/api/media/files").with(user("anna").roles("SPEAKER")))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void foreignTalkIsRejected() throws Exception {
        String talkId = json.readTree(mockMvc.perform(post("/api/speaker-talks").with(user("timur").roles("SPEAKER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Чужой\",\"topic\":\"Go\"}"))
                .andReturn().getResponse().getContentAsString()).get("id").asText();

        MockMultipartFile file = new MockMultipartFile("file", "slides.pdf", "application/pdf", new byte[]{1});
        mockMvc.perform(multipart("/api/media/upload").file(file).param("talkId", talkId).with(user("anna").roles("SPEAKER")))
                .andExpect(status().isBadRequest());
    }
}
