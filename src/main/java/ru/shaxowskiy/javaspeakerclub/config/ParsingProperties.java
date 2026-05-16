package ru.shaxowskiy.javaspeakerclub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app.parsing")
public record ParsingProperties(
        String searchApiKey,
        String serperUrl,
        int maxPagesPerRun,
        int maxHtmlChars,
        Duration requestTimeout,
        String userAgent,
        int maxResponseBytes,
        int serperResultsPerQuery,
        List<String> blockedDomains
) {
}
