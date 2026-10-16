package ru.shaxowskiy.javaspeakerclub.parsing.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import ru.shaxowskiy.javaspeakerclub.config.ParsingProperties;
import ru.shaxowskiy.javaspeakerclub.parsing.model.SerperSearchResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebSearchToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    void serperResponseDeserializesOrganicLinksFromFixture() throws Exception {
        String body = new String(
                getClass().getResourceAsStream("/parsing/serper-response.json").readAllBytes(),
                StandardCharsets.UTF_8
        );

        SerperSearchResponse response = objectMapper.readValue(body, SerperSearchResponse.class);

        List<String> links = response.links();
        assertEquals(2, links.size());
        assertEquals("https://jpoint.ru/2026/program", links.get(0));
        assertTrue(links.get(1).contains("heisenbug.ru"));
    }

    @Test
    void searchReturnsEmptyWhenApiKeyMissing() {
        ParsingProperties properties = new ParsingProperties(
                "",
                serperUrl(),
                10,
                50_000,
                Duration.ofSeconds(5),
                "TestBot",
                1_048_576,
                15,
                List.of()
        );
        WebSearchTool tool = new WebSearchTool(WebClient.builder().build(), properties);

        assertTrue(tool.search("test query").isEmpty());
    }

    @Test
    void searchForDateSendsSerperRequestWithoutSiteOperators() throws Exception {
        enqueueSerperResponse();
        enqueueSerperResponse();
        enqueueSerperResponse();

        WebSearchTool tool = webSearchTool(15);
        tool.searchForDate(LocalDate.of(2026, 5, 16), null);

        assertEquals(3, mockWebServer.getRequestCount());
        for (int i = 0; i < 3; i++) {
            RecordedRequest request = mockWebServer.takeRequest();
            JsonNode json = objectMapper.readTree(request.getBody().readUtf8());
            String query = json.path("q").asText().toLowerCase(Locale.ROOT);
            assertFalse(query.contains("site:"), () -> "query must not contain site operators: " + query);
            assertEquals("ru", json.path("gl").asText());
            assertEquals("ru", json.path("hl").asText());
            assertEquals(15, json.path("num").asInt());
        }
    }

    @Test
    void searchCapsNumWhenUserHintContainsSiteOperator() throws Exception {
        enqueueSerperResponse();

        WebSearchTool tool = webSearchTool(15);
        tool.search("conference -site:example.com");

        RecordedRequest request = mockWebServer.takeRequest();
        JsonNode json = objectMapper.readTree(request.getBody().readUtf8());
        assertEquals(10, json.path("num").asInt());
        assertEquals("ru", json.path("gl").asText());
        assertEquals("ru", json.path("hl").asText());
    }

    private WebSearchTool webSearchTool(int serperResultsPerQuery) {
        ParsingProperties properties = new ParsingProperties(
                "test-key",
                serperUrl(),
                10,
                50_000,
                Duration.ofSeconds(5),
                "TestBot",
                1_048_576,
                serperResultsPerQuery,
                List.of()
        );
        return new WebSearchTool(WebClient.builder().build(), properties);
    }

    private String serperUrl() {
        return mockWebServer.url("/search").toString();
    }

    private void enqueueSerperResponse() {
        mockWebServer.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("{\"organic\":[{\"link\":\"https://jpoint.ru/2026/program\"}]}"));
    }
}
