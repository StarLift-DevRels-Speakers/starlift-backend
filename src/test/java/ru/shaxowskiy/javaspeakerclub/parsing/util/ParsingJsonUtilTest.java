package ru.shaxowskiy.javaspeakerclub.parsing.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ParsingJsonUtilTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void extractsJsonFromMarkdownFence() throws Exception {
        String raw = """
                ```json
                {"skipped":false,"region":"Москва","speakers":[{"conference":"JPoint","speakerName":"Иван","company":"Сбер"}]}
                ```
                """;
        JsonNode node = objectMapper.readTree(ParsingJsonUtil.extractJsonObject(raw));
        assertFalse(node.path("skipped").asBoolean());
        assertEquals("Москва", node.path("region").asText());
        assertEquals("Иван", node.path("speakers").get(0).path("speakerName").asText());
    }
}
