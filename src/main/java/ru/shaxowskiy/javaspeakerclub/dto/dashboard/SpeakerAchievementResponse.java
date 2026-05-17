package ru.shaxowskiy.javaspeakerclub.dto.dashboard;

import java.time.LocalDateTime;

public record SpeakerAchievementResponse(
        String id,
        Long speakerId,
        String title,
        String description,
        Integer current,
        Integer target,
        LocalDateTime createdDate
) {
}
