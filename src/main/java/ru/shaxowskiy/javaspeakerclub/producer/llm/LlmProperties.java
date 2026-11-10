package ru.shaxowskiy.javaspeakerclub.producer.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Ограничения вызовов LLM. Тариф GigaChat допускает один параллельный запрос, поэтому по умолчанию вызовы идут
 * по одному, а ответ 429 повторяется с растущей паузой.
 */
@ConfigurationProperties(prefix = "app.llm")
public record LlmProperties(
        @DefaultValue("1") int maxConcurrent,
        @DefaultValue("3") int rateLimitRetries,
        @DefaultValue("3s") Duration rateLimitDelay
) {
}
