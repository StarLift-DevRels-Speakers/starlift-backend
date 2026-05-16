package ru.shaxowskiy.javaspeakerclub.parsing.model;

import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels.ParsingStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ParsingEventResponse(
        UUID id,
        String conference,
        String speakerName,
        String company,
        String sourceUrl,
        LocalDate eventDate,
        String region,
        ParsingStatus status,
        Long speakerId,
        String note,
        LocalDateTime createdAt
) {
}
