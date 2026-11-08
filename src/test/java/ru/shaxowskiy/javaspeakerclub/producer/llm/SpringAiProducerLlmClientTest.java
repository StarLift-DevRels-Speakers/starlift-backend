package ru.shaxowskiy.javaspeakerclub.producer.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitDraft;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiProducerLlmClientTest {

    private static final List<Long> SLEEPS = new CopyOnWriteArrayList<>();

    private final SpringAiProducerLlmClient client = client();

    @BeforeEach
    void resetSleeps() {
        SLEEPS.clear();
    }

    @Test
    void retriesRateLimitedCallsWithGrowingPause() {
        AtomicInteger calls = new AtomicInteger();

        String result = client.withLimits(() -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("HTTP 429 - {\"message\":\"Too many requests\"}");
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(SLEEPS).containsExactly(3000L, 6000L);
    }

    @Test
    void givesUpAfterConfiguredRetries() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> client.withLimits(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("Too many requests");
        })).hasMessageContaining("Too many requests");
        assertThat(calls).hasValue(4);
    }

    @Test
    void otherErrorsAreNotRetried() {
        AtomicInteger calls = new AtomicInteger();

        assertThatThrownBy(() -> client.withLimits(() -> {
            calls.incrementAndGet();
            throw new IllegalStateException("HTTP 500");
        })).hasMessage("HTTP 500");
        assertThat(calls).hasValue(1);
        assertThat(SLEEPS).isEmpty();
    }

    @Test
    void detectsRateLimitInCauseChain() {
        assertThat(SpringAiProducerLlmClient.isRateLimited(new RuntimeException("wrapped", new RuntimeException("status 429")))).isTrue();
        assertThat(SpringAiProducerLlmClient.isRateLimited(new RuntimeException("HTTP 401"))).isFalse();
    }

    @Test
    void callsRunOneAtATime() throws Exception {
        AtomicInteger running = new AtomicInteger();
        AtomicInteger maxRunning = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> client.withLimits(() -> {
                    maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    running.decrementAndGet();
                    return "ok";
                })));
            }
            for (Future<String> future : futures) {
                assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo("ok");
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(maxRunning).hasValue(1);
    }

    @Test
    void parsesAnswerWithUnescapedQuotesInComment() {
        // Форма ответа GigaChat, на котором упала проверка презентации на стенде.
        String answer = """
                ```json
                {"verdict": "partially", "criteria": [
                  {"key": "level", "score": 6, "comment": "Слайды 2–3 объясняют основы"},
                  {"key": "turnoffs", "score": 3, "comment": "Слайд "Наш инструмент ExpLab": прямая реклама"}
                ], "issues": [{"fragmentId": "slide-7", "problem": "Реклама "ExpLab"", "fix": "Убери слайд"}]}
                ```""";

        AudienceFitDraft draft = client.parse(answer, AudienceFitDraft.class);

        assertThat(draft.criteria()).hasSize(2);
        assertThat(draft.criteria().get(1).comment()).isEqualTo("Слайд \"Наш инструмент ExpLab\": прямая реклама");
        assertThat(draft.issues().getFirst().problem()).isEqualTo("Реклама \"ExpLab\"");
    }

    @Test
    void stillClosesTruncatedAnswer() {
        AudienceFitDraft draft = client.parse("{\"verdict\": \"fits\", \"criteria\": [{\"key\": \"level\", \"score\": 9}]", AudienceFitDraft.class);

        assertThat(draft.verdict()).isEqualTo("fits");
        assertThat(draft.criteria()).hasSize(1);
    }

    @Test
    void throwsWhenNothingHelps() {
        assertThatThrownBy(() -> client.parse("{\"criteria\": [1, 2}", AudienceFitDraft.class))
                .isInstanceOf(RuntimeException.class);
    }

    private static SpringAiProducerLlmClient client() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        ProducerProperties properties = new ProducerProperties(3, 3, 4, 365, 80, 12, 3, 0.2, 10, 5, "producer-v2");
        SpringAiProducerLlmClient client = new SpringAiProducerLlmClient(builder, new ProducerJson(), properties,
                new LlmProperties(1, 3, Duration.ofSeconds(3)), "GigaChat-2-Max");
        client.setSleeper(millis -> SLEEPS.add(millis));
        return client;
    }
}
