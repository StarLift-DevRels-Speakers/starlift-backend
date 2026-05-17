package ru.shaxowskiy.javaspeakerclub.dto.dashboard;

public record SpeakerAchievementUpsertRequest(
        String id,
        String title,
        String description,
        Integer current,
        Integer target
) {
}
