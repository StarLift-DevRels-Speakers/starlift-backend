package ru.shaxowskiy.javaspeakerclub.parsing.tool;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import ru.shaxowskiy.javaspeakerclub.config.ParsingProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageFetchToolTest {

    @Test
    void convertsHtmlFixtureToText() throws Exception {
        String html = new String(
                getClass().getResourceAsStream("/parsing/sample-program.html").readAllBytes(),
                StandardCharsets.UTF_8
        );

        ParsingProperties properties = new ParsingProperties(
                "key",
                "https://google.serper.dev/search",
                10,
                50_000,
                Duration.ofSeconds(5),
                "TestBot",
                5_242_880,
                15,
                List.of()
        );

        PageFetchTool tool = new PageFetchTool(WebClient.builder().build(), properties);
        String text = tool.toPlainText(html, "https://example.com/program");

        assertNotNull(text);
        assertTrue(text.contains("JPoint 2026"));
        assertTrue(text.contains("Иван Иванов"));
        assertTrue(text.contains("Сбер"));
    }
}
