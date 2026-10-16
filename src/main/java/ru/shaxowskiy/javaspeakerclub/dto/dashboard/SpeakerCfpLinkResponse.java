package ru.shaxowskiy.javaspeakerclub.dto.dashboard;

import java.time.LocalDateTime;

public record SpeakerCfpLinkResponse(
        Long speakerId,
        String recommendationId,
        Integer matchScore,
        LocalDateTime createdDate
) {
}
