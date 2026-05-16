package ru.shaxowskiy.javaspeakerclub.parsing.agent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.shaxowskiy.javaspeakerclub.config.ParsingProperties;
import ru.shaxowskiy.javaspeakerclub.parsing.model.PageExtractionResult;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsedSpeakerRow;
import ru.shaxowskiy.javaspeakerclub.parsing.tool.PageFetchTool;
import ru.shaxowskiy.javaspeakerclub.parsing.tool.SpeakerExtractionTool;
import ru.shaxowskiy.javaspeakerclub.parsing.tool.WebSearchTool;
import ru.shaxowskiy.javaspeakerclub.parsing.util.ParsedSpeakerRowValidator;
import ru.shaxowskiy.javaspeakerclub.parsing.util.ParsingUrlFilter;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConferenceParsingAgentTest {

    @Mock
    private WebSearchTool webSearchTool;
    @Mock
    private PageFetchTool pageFetchTool;
    @Mock
    private SpeakerExtractionTool speakerExtractionTool;
    @Mock
    private ParsingProperties parsingProperties;
    @Mock
    private ParsingUrlFilter parsingUrlFilter;

    private final ParsedSpeakerRowValidator speakerRowValidator = new ParsedSpeakerRowValidator();
    private ConferenceParsingAgent agent;

    @BeforeEach
    void setUp() {
        agent = new ConferenceParsingAgent(
                webSearchTool,
                pageFetchTool,
                speakerExtractionTool,
                parsingProperties,
                parsingUrlFilter,
                speakerRowValidator
        );
    }

    @Test
    void runAggregatesSpeakersFromSearchResults() {
        LocalDate date = LocalDate.of(2026, 5, 16);
        String url = "https://jpoint.ru/conf/program";

        when(parsingProperties.maxPagesPerRun()).thenReturn(5);
        when(webSearchTool.searchForDate(date, null)).thenReturn(List.of(url));
        when(parsingUrlFilter.prioritize(List.of(url))).thenReturn(List.of(url));
        when(pageFetchTool.fetchPageText(url)).thenReturn("JPoint speakers program");
        when(speakerExtractionTool.extractFromPage(anyString(), anyString(), any(), any()))
                .thenReturn(new PageExtractionResult(
                        "Москва",
                        List.of(new ParsedSpeakerRow("JPoint 2026", "Иван Иванов", "Сбер")),
                        false
                ));

        ConferenceParsingAgent.AgentRunResult result = agent.run(date, null);

        assertFalse(result.candidates().isEmpty());
        assertEquals("Иван Иванов", result.candidates().getFirst().row().speakerName());
        assertEquals(url, result.candidates().getFirst().sourceUrl());
    }

    @Test
    void runRejectsPlaceholderSpeakers() {
        LocalDate date = LocalDate.of(2026, 5, 16);
        String url = "https://cppconf.ru/speakers";

        when(parsingProperties.maxPagesPerRun()).thenReturn(5);
        when(webSearchTool.searchForDate(date, null)).thenReturn(List.of(url));
        when(parsingUrlFilter.prioritize(List.of(url))).thenReturn(List.of(url));
        when(pageFetchTool.fetchPageText(url)).thenReturn("page");
        when(speakerExtractionTool.extractFromPage(anyString(), anyString(), any(), any()))
                .thenReturn(new PageExtractionResult(
                        "Москва",
                        List.of(
                                new ParsedSpeakerRow("C++ Russia", "неизвестен", null),
                                new ParsedSpeakerRow("C++ Russia", "Алексей Смирнов", "VK")
                        ),
                        false
                ));

        ConferenceParsingAgent.AgentRunResult result = agent.run(date, null);

        assertEquals(1, result.candidates().size());
        assertEquals(1, result.rejectedPlaceholders());
        assertEquals("Алексей Смирнов", result.candidates().getFirst().row().speakerName());
    }
}
