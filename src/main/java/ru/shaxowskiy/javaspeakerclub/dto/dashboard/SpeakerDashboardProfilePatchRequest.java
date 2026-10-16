package ru.shaxowskiy.javaspeakerclub.dto.dashboard;

import java.time.LocalDateTime;

public record SpeakerDashboardProfilePatchRequest(
        String region,
        String company,
        DashboardInfluenceDto influence,
        Boolean topConferenceCandidate,
        String avatarColor,
        LocalDateTime lastEvaluationDate
) {
}
