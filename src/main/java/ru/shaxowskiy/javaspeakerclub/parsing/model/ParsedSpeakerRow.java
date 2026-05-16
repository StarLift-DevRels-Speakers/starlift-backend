package ru.shaxowskiy.javaspeakerclub.parsing.model;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParsedSpeakerRow(
        String conference,
        String speakerName,
        String company
) {
}
