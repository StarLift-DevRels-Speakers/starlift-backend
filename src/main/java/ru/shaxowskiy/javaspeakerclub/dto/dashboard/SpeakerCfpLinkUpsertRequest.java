package ru.shaxowskiy.javaspeakerclub.dto.dashboard;

public record SpeakerCfpLinkUpsertRequest(
        String recommendationId,
        Integer matchScore
) {
}
