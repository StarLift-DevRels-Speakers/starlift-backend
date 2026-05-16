package ru.shaxowskiy.javaspeakerclub.parsing.model;

import java.time.LocalDate;

public record ParsingRunRequest(
        LocalDate date,
        String hint
) {
}
