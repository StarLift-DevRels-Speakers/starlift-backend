package ru.shaxowskiy.javaspeakerclub.parsing.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.parsing.model.PageExtractionResult;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsedSpeakerRow;
import ru.shaxowskiy.javaspeakerclub.parsing.util.ParsingJsonUtil;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SpeakerExtractionTool {

    private static final TypeReference<List<ParsedSpeakerRow>> SPEAKER_LIST_TYPE = new TypeReference<>() {
    };

    private final ChatClient.Builder chatClientBuilder;
    private final ObjectMapper objectMapper;

    public PageExtractionResult extractFromPage(String pageUrl, String pageText, LocalDate targetDate, String userHint) {
        if (pageText == null || pageText.isBlank()) {
            return PageExtractionResult.empty();
        }

        String hintSuffix = userHint == null || userHint.isBlank() ? "" : "\nДополнительная подсказка: " + userHint;

        String prompt = """
                Извлеки спикеров IT-конференций и митапов из текста страницы.
                URL: %s
                Целевая дата (Europe/Moscow): %s
                %s

                Правила:
                1. Включай только мероприятия в России (город в РФ или страна Россия; российские онлайн-ивенты — ок).
                2. Если локация явно не в России — верни {"skipped":true,"region":null,"speakers":[]}.
                3. Если страница — календарь/афиша/листинг событий БЕЗ ФИО докладчиков — верни speakers:[] (не выдумывай).
                4. Только реальные докладчики из программы/speakers на целевую дату (для многодневных — только этот день).
                5. Один спикер = один объект в speakers[]. У одной конференции может быть много объектов с одинаковым conference.
                6. Запрещено в speakerName: null, неизвестен, unknown, N/A, —, название конференции, название доклада без человека.
                7. company — только если указана у этого спикера; иначе JSON null (не строка "null").
                8. Не включай модераторов без доклада, спонсоров, HR/медицину/строительство, если это не IT-ивент.

                Ответ — только JSON без markdown:
                {"skipped":false,"region":"Москва","speakers":[{"conference":"...","speakerName":"Иван Иванов","company":"Сбер"}]}
                """.formatted(pageUrl, targetDate, hintSuffix);

        try {
            ChatClient chatClient = chatClientBuilder.build();
            String content = chatClient.prompt()
                    .user(prompt + "\n\n--- PAGE TEXT ---\n" + pageText)
                    .call()
                    .content();

            String json = ParsingJsonUtil.extractJsonObject(content);
            JsonNode root = objectMapper.readTree(json);
            if (root.path("skipped").asBoolean(false)) {
                return PageExtractionResult.empty();
            }

            String region = root.path("region").isNull() ? null : root.path("region").asText(null);
            List<ParsedSpeakerRow> speakers = objectMapper.convertValue(
                    root.path("speakers"),
                    SPEAKER_LIST_TYPE
            );
            if (speakers == null) {
                speakers = List.of();
            }
            return new PageExtractionResult(region, speakers, false);
        } catch (Exception e) {
            log.warn("Speaker extraction failed for url={}: {}", pageUrl, e.getMessage());
            return PageExtractionResult.empty();
        }
    }
}
