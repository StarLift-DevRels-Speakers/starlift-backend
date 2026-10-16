package ru.shaxowskiy.javaspeakerclub.parsing.model;

import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels.ParsingStatus;

public record ParsingEventPatchRequest(
        ParsingStatus status,
        Long speakerId,
        String topic,
        String note
) {
}
