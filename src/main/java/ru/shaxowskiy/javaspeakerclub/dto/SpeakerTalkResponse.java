package ru.shaxowskiy.javaspeakerclub.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SpeakerTalkResponse(
        UUID id,
        Long speakerId,
        String name,
        String topic,
        String description,
        LocalDateTime createdDate
) {}
