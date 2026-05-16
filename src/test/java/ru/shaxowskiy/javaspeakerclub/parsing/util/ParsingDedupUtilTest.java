package ru.shaxowskiy.javaspeakerclub.parsing.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ParsingDedupUtilTest {

    @Test
    void dedupKeyIsStableForSameInput() {
        LocalDate date = LocalDate.of(2026, 5, 16);
        String key1 = ParsingDedupUtil.dedupKey("JPoint", "Иван Иванов", date, "https://jpoint.ru/program");
        String key2 = ParsingDedupUtil.dedupKey("jpoint", "иван иванов", date, "https://jpoint.ru/program");
        assertEquals(key1, key2);
    }

    @Test
    void dedupKeyChangesWhenSpeakerChanges() {
        LocalDate date = LocalDate.of(2026, 5, 16);
        String key1 = ParsingDedupUtil.dedupKey("JPoint", "Иван Иванов", date, "https://jpoint.ru/program");
        String key2 = ParsingDedupUtil.dedupKey("JPoint", "Пётр Петров", date, "https://jpoint.ru/program");
        assertNotEquals(key1, key2);
    }
}
