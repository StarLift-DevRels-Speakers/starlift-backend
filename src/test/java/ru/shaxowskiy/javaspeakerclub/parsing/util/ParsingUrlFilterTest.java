package ru.shaxowskiy.javaspeakerclub.parsing.util;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.config.ParsingProperties;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParsingUrlFilterTest {

    private final ParsingUrlFilter filter = new ParsingUrlFilter(testProperties());

    @Test
    void blocksAggregatorDomains() {
        assertFalse(filter.isProcessable("https://all-events.ru/events/calendar/"));
        assertFalse(filter.isProcessable("https://workspace.ru/events/konferenciya/"));
    }

    @Test
    void boostsSpeakerPages() {
        int programScore = filter.score("https://jpoint.ru/2026/program");
        int calendarScore = filter.score("https://example.com/events/calendar/theme");
        assertTrue(programScore > calendarScore);
    }

    @Test
    void prioritizesSpeakerUrlsFirst() {
        List<String> sorted = filter.prioritize(List.of(
                "https://all-events.ru/events/calendar/",
                "https://cppconf.ru/2026/speakers",
                "https://unknown.com/events/calendar/list"
        ));
        assertEquals("https://cppconf.ru/2026/speakers", sorted.getFirst());
        assertFalse(sorted.stream().anyMatch(url -> url.contains("all-events.ru")));
    }

    private static ParsingProperties testProperties() {
        return new ParsingProperties(
                "key",
                "https://google.serper.dev/search",
                10,
                50_000,
                Duration.ofSeconds(5),
                "TestBot",
                1_048_576,
                15,
                List.of()
        );
    }
}
