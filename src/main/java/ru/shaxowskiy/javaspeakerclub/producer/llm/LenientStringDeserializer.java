package ru.shaxowskiy.javaspeakerclub.producer.llm;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.util.ArrayList;
import java.util.List;

/**
 * Строковое поле ответа LLM. GigaChat иногда отвечает списком там, где ждём строку:
 * "value": ["русский", "английский"] — значения склеиваются через запятую, а не роняют весь разбор.
 */
public class LenientStringDeserializer extends ValueDeserializer<String> {

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
        if (parser.currentToken() != JsonToken.START_ARRAY && parser.currentToken() != JsonToken.START_OBJECT) {
            return parser.getValueAsString();
        }
        JsonNode node = context.readTree(parser);
        List<String> parts = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isValueNode() && !item.isNull() && !item.asString().isBlank()) {
                parts.add(item.asString().trim());
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }
}
