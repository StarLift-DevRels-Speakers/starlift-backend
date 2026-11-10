package ru.shaxowskiy.javaspeakerclub.audience;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Проверка материалов спикера под ожидаемую аудиторию мероприятия.
 * Пороги вердикта — по итоговой оценке 0–100, которую считает сервер, а не модель.
 */
@ConfigurationProperties(prefix = "app.audience")
public record AudienceProperties(
        @DefaultValue("true") boolean triggersEnabled,
        @DefaultValue("event-audience-v3") String profilePromptVersion,
        @DefaultValue("audience-fit-v3") String fitPromptVersion,
        @DefaultValue("2500") int maxResponseTokens,
        @DefaultValue("30000") int maxMaterialChars,
        @DefaultValue("1500") int maxFragmentChars,
        @DefaultValue("200") int minMaterialChars,
        @DefaultValue("200") int maxPages,
        @DefaultValue("20") int acceptedTalksLimit,
        @DefaultValue("30") int pastEditionsLimit,
        @DefaultValue("70") int fitThreshold,
        @DefaultValue("45") int partialThreshold,
        @DefaultValue("10") int pendingTimeoutMinutes
) {
}
