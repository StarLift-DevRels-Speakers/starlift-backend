package ru.shaxowskiy.javaspeakerclub.signals;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Настройки интеграции с сервисом weak-signals (оценка потенциала спикера).
 * Таймаут больше таймаута GigaChat внутри сервиса (60s), чтобы получать его собственный 504, а не обрывать запрос.
 */
@ConfigurationProperties(prefix = "app.weak-signals")
public record WeakSignalsProperties(
        @DefaultValue("http://localhost:8000") String baseUrl,
        @DefaultValue("90s") Duration requestTimeout,
        @DefaultValue("70") int cfpHighMatchThreshold,
        @DefaultValue("10") int recentTalksLimit,
        @DefaultValue("20") int recentFeedbackLimit,
        @DefaultValue("10") int achievementsLimit,
        @DefaultValue("10") int parsingEventsLimit
) {
}
