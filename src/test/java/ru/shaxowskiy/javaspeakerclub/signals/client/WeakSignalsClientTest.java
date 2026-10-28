package ru.shaxowskiy.javaspeakerclub.signals.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.signals.WeakSignalsProperties;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentRequest;
import ru.shaxowskiy.javaspeakerclub.signals.model.TalentScoreResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WeakSignalsClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void sendsSnakeCaseBodyAndParsesScore() throws Exception {
        server.enqueue(json(200, """
                {"id": 42, "score": 4, "reason": "Растёт", "confidence": "high"}
                """));

        TalentScoreResponse response = client(Duration.ofSeconds(5)).score(request());

        assertThat(response).isEqualTo(new TalentScoreResponse(42L, 4, "Растёт", "high"));

        RecordedRequest recorded = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/signals/weak-signals");

        JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());
        assertThat(body.get("id").asLong()).isEqualTo(42L);
        assertThat(body.get("talks_last_3_months").asInt()).isEqualTo(1);
        assertThat(body.get("last_talk_date").asText()).isEqualTo("2026-09-28T10:00:00");
        assertThat(body.get("nps").decimalValue()).isEqualByComparingTo("8.5");
        assertThat(body.get("avg_rating").asDouble()).isEqualTo(5.4);
        assertThat(body.get("achievements_progress_pct").asDouble()).isEqualTo(42.0);
        assertThat(body.get("region").isNull()).isTrue();
        assertThat(body.get("recent_talks").get(0).get("conference_type").asText()).isEqualTo("MEETUP");
        assertThat(body.get("recent_feedback").get(0).has("improvement_suggestions")).isTrue();
        assertThat(body.get("parsing_events").get(0).get("event_date").asText()).isEqualTo("2026-10-01");
        assertThat(body.get("profile_tags").get(0).asText()).isEqualTo("kafka");
        assertThat(body.has("talksTotal")).isFalse();
    }

    @Test
    void upstreamValidationErrorBecomesBadGateway() {
        server.enqueue(json(422, """
                {"detail": [{"loc": ["body", "avg_rating"], "msg": "Input should be a valid number"}]}
                """));

        assertStatus(HttpStatus.BAD_GATEWAY);
    }

    @Test
    void upstreamTimeoutBecomesGatewayTimeout() {
        server.enqueue(json(504, """
                {"detail": "Scoring provider timed out"}
                """));

        assertStatus(HttpStatus.GATEWAY_TIMEOUT);
    }

    @Test
    void slowUpstreamBecomesGatewayTimeout() {
        server.enqueue(json(200, "{}").setBodyDelay(2, TimeUnit.SECONDS));

        assertThatThrownBy(() -> client(Duration.ofMillis(200)).score(request()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT.value()));
    }

    @Test
    void unreachableUpstreamBecomesBadGateway() throws IOException {
        WeakSignalsClient client = client(Duration.ofSeconds(5));
        server.shutdown();

        assertThatThrownBy(() -> client.score(request()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(HttpStatus.BAD_GATEWAY.value()));
    }

    private void assertStatus(HttpStatus expected) {
        assertThatThrownBy(() -> client(Duration.ofSeconds(5)).score(request()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode().value()).isEqualTo(expected.value()));
    }

    private WeakSignalsClient client(Duration timeout) {
        String baseUrl = server.url("/").toString();
        WeakSignalsProperties properties = new WeakSignalsProperties(baseUrl, timeout, 70, 10, 20, 10, 10);
        return new WeakSignalsClient(WebClient.builder().baseUrl(baseUrl).build(), properties);
    }

    private static MockResponse json(int status, String body) {
        return new MockResponse()
                .setResponseCode(status)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private static TalentRequest request() {
        LocalDateTime talkDate = LocalDateTime.of(2026, 9, 28, 10, 0);
        return new TalentRequest(
                42L, "ivan_petrov", null, "Ozon", "Senior Backend Engineer", null, "federal",
                5, 1, talkDate, 5.4, 0, new BigDecimal("8.5"), 90, 42.0, 2,
                List.of(new TalentRequest.TalkItem("Kafka в проде", "MEETUP", "OFFLINE", talkDate)),
                List.of(new TalentRequest.FeedbackItem(9, "Живо", "Быстро", "Паузы")),
                List.of(),
                List.of(new TalentRequest.ParsingEventItem("HighLoad++", null, LocalDate.of(2026, 10, 1), null)),
                List.of("kafka")
        );
    }
}
