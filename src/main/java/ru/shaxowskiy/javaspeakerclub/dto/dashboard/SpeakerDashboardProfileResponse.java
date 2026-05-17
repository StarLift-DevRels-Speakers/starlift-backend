package ru.shaxowskiy.javaspeakerclub.dto.dashboard;

import java.time.LocalDateTime;

public record SpeakerDashboardProfileResponse(
        Long speakerId,
        String region,
        String company,
        DashboardInfluenceDto influence,
        Integer profileCompletion,
        Integer audience,
        LocalDateTime lastEvaluationDate,
        Boolean topConferenceCandidate,
        String avatarColor
) {
}
