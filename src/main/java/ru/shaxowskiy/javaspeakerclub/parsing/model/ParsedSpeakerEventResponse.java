package ru.shaxowskiy.javaspeakerclub.parsing.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ParsedSpeakerEventResponse(
        String conference,
        String speakerName,
        String company,
        String sourceUrl,
        LocalDate eventDate,
        String region,
        LocalDateTime createdAt
) {
}
