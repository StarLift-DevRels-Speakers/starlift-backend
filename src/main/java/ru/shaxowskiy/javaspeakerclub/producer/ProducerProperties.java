package ru.shaxowskiy.javaspeakerclub.producer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.producer")
public record ProducerProperties(
        @DefaultValue("3") int minThemeMentions,
        @DefaultValue("3") int maxSteps,
        @DefaultValue("4") int maxThemesPerKind,
        @DefaultValue("365") int feedbackWindowDays,
        @DefaultValue("80") int maxFeedbackItems,
        @DefaultValue("12") int recentTalksLimit,
        @DefaultValue("3") int minFeedbackForPlan,
        @DefaultValue("0.2") double temperature,
        @DefaultValue("10") int regenerateAllLimit,
        @DefaultValue("producer-v1") String promptVersion
) {
}
