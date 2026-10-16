package ru.shaxowskiy.javaspeakerclub.parsing.util;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsedSpeakerRow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsedSpeakerRowValidatorTest {

    private final ParsedSpeakerRowValidator validator = new ParsedSpeakerRowValidator();

    @Test
    void rejectsPlaceholderNames() {
        assertFalse(validator.isValid(new ParsedSpeakerRow("Conf", "неизвестен", null)));
        assertFalse(validator.isValid(new ParsedSpeakerRow("Conf", "null", "Яндекс")));
        assertFalse(validator.isValid(new ParsedSpeakerRow("Conf", "unknown", null)));
    }

    @Test
    void acceptsRealSpeakerName() {
        assertTrue(validator.isValid(new ParsedSpeakerRow("JPoint 2026", "Иван Иванов", "Сбер")));
    }

    @Test
    void normalizesNullCompanyString() {
        ParsedSpeakerRow normalized = validator.normalizeRow(
                new ParsedSpeakerRow("JPoint", "Иван Иванов", "null")
        );
        assertNull(normalized.company());
    }

    @Test
    void acceptsMultipleSpeakersSameConference() {
        ParsedSpeakerRow first = new ParsedSpeakerRow("C++ Russia 2026", "Алексей Иванов", "Yandex");
        ParsedSpeakerRow second = new ParsedSpeakerRow("C++ Russia 2026", "Мария Петрова", null);
        assertTrue(validator.isValid(first));
        assertTrue(validator.isValid(second));
        assertEquals("C++ Russia 2026", first.conference());
        assertEquals("C++ Russia 2026", second.conference());
    }
}
