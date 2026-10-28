package ru.shaxowskiy.javaspeakerclub.gamification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.gamification")
public record GamificationProperties(
        @DefaultValue("Europe/Moscow") String zone,
        @DefaultValue("true") boolean scheduledCloseEnabled,
        @DefaultValue("3") int promotionCount,
        @DefaultValue("3") int demotionCount,
        @DefaultValue("6") int minParticipantsForDemotion,
        @DefaultValue("9") int highRatingThreshold,
        @DefaultValue("7") int streakBonusEveryDays,
        @DefaultValue("200") int feedbackDailyCap,
        @DefaultValue("1000") int manualMaxAbs,
        @DefaultValue Points points
) {

    public record Points(
            @DefaultValue("50") int talkCreated,
            @DefaultValue("10") int feedbackReceived,
            @DefaultValue("15") int highRatingFeedback,
            @DefaultValue("100") int profileCompleted,
            @DefaultValue("5") int dailyCheckIn,
            @DefaultValue("25") int streakBonus
    ) {
    }
}
