package ru.shaxowskiy.javaspeakerclub.parsing.model;

import java.util.List;

public record ParsingRunResponse(
        List<ParsedSpeakerRow> speakers,
        int newEvents,
        int duplicateEvents,
        List<String> scannedUrls,
        List<String> errors,
        int skippedUrls,
        int rejectedPlaceholders
) {
}
