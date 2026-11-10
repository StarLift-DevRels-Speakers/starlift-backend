package ru.shaxowskiy.javaspeakerclub.producer.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.parsing.util.ParsingJsonUtil;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerProperties;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft;

import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Slf4j
@Component
public class SpringAiProducerLlmClient implements ProducerLlmClient {

    private final ChatClient chatClient;
    private final ProducerJson json;
    private final ProducerProperties properties;
    private final LlmProperties limits;
    private final String model;
    private final Semaphore permits;
    private Sleeper sleeper = Thread::sleep;

    /**
     * Пауза между повторами; в тестах подменяется, чтобы не ждать.
     */
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    public SpringAiProducerLlmClient(
            ChatClient.Builder chatClientBuilder,
            ProducerJson json,
            ProducerProperties properties,
            LlmProperties limits,
            @Value("${spring.ai.gigachat.chat.model:unknown}") String model
    ) {
        this.chatClient = chatClientBuilder.build();
        this.json = json;
        this.properties = properties;
        this.limits = limits;
        this.model = model;
        this.permits = new Semaphore(Math.max(1, limits.maxConcurrent()), true);
    }

    void setSleeper(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    @Override
    public ProducerPlanDraft generatePlan(String systemPrompt, String userMessage) {
        return generate(systemPrompt, userMessage, ProducerPlanDraft.class, null);
    }

    @Override
    public <T> T generate(String systemPrompt, String userMessage, Class<T> type, Integer maxTokens) {
        String content = withLimits(() -> {
            // Сообщения передаём объектами: .system(String) прогоняет текст через шаблонизатор,
            // а в промпте есть фигурные скобки JSON-схемы.
            return chatClient.prompt()
                    .messages(new SystemMessage(systemPrompt), new UserMessage(userMessage))
                    .options(ChatOptions.builder().temperature(properties.temperature()).maxTokens(maxTokens))
                    .call()
                    .content();
        });
        return parse(content, type);
    }

    /**
     * Не больше maxConcurrent вызовов одновременно; на 429 — повтор с паузой rateLimitDelay × номер попытки.
     */
    <T> T withLimits(Supplier<T> call) {
        try {
            permits.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Прерван вызов LLM", e);
        }
        try {
            for (int attempt = 0; ; attempt++) {
                try {
                    return call.get();
                } catch (RuntimeException e) {
                    if (!isRateLimited(e) || attempt >= limits.rateLimitRetries()) {
                        throw e;
                    }
                    long delay = limits.rateLimitDelay().toMillis() * (attempt + 1);
                    log.info("LLM rate limited, retry {} of {} in {} ms", attempt + 1, limits.rateLimitRetries(), delay);
                    sleeper.sleep(delay);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Прерван вызов LLM", e);
        } finally {
            permits.release();
        }
    }

    static boolean isRateLimited(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null && (message.contains("429") || message.toLowerCase(Locale.ROOT).contains("too many requests"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ответ как есть, затем с дописанными скобками, затем с экранированными кавычками внутри строк.
     * Если не помогло ничего, бросается исходная ошибка разбора.
     */
    <T> T parse(String content, Class<T> type) {
        try {
            return json.read(ParsingJsonUtil.extractJsonObject(content), type);
        } catch (RuntimeException e) {
            String escaped = JsonRepair.escapeInnerQuotes(content);
            for (String candidate : new String[]{
                    JsonRepair.closeUnbalanced(content),
                    escaped,
                    JsonRepair.closeUnbalanced(escaped)
            }) {
                if (candidate == null) {
                    continue;
                }
                try {
                    return json.read(ParsingJsonUtil.extractJsonObject(candidate), type);
                } catch (RuntimeException ignored) {
                    // следующий вариант починки
                }
            }
            throw e;
        }
    }

    @Override
    public String modelName() {
        return model;
    }
}
