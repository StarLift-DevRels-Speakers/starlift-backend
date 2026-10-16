package ru.shaxowskiy.javaspeakerclub.parsing.model;

import java.util.List;

public record PageExtractionResult(
        String region,
        List<ParsedSpeakerRow> speakers,
        boolean skipped
) {
    public static PageExtractionResult empty() {
        return new PageExtractionResult(null, List.of(), true);
    }
}
