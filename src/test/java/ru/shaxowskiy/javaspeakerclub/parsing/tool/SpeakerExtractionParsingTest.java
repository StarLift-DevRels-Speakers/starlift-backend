package ru.shaxowskiy.javaspeakerclub.parsing.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsedSpeakerRow;
import ru.shaxowskiy.javaspeakerclub.parsing.util.ParsedSpeakerRowValidator;
import ru.shaxowskiy.javaspeakerclub.parsing.util.ParsingJsonUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpeakerExtractionParsingTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final ParsedSpeakerRowValidator validator = new ParsedSpeakerRowValidator();

  @Test
  void parsesMultipleSpeakersForOneConference() throws Exception {
    String json = new String(
        getClass().getResourceAsStream("/parsing/extraction-multi-speakers.json").readAllBytes(),
        StandardCharsets.UTF_8
    );
    JsonNode root = objectMapper.readTree(ParsingJsonUtil.extractJsonObject(json));
    List<ParsedSpeakerRow> speakers = objectMapper.convertValue(
        root.path("speakers"),
        objectMapper.getTypeFactory().constructCollectionType(List.class, ParsedSpeakerRow.class)
    );

    assertEquals(3, speakers.size());
    assertEquals(3, speakers.stream().filter(row -> "JPoint 2026".equals(row.conference())).count());
    assertTrue(speakers.stream().allMatch(validator::isValid));
  }
}
