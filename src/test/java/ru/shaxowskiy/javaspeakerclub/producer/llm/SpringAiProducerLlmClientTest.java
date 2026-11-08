package ru.shaxowskiy.javaspeakerclub.producer.llm;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitDraft;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpringAiProducerLlmClientTest {

    private final SpringAiProducerLlmClient client = client();

    @Test
    void parsesAnswerWithUnescapedQuotesInComment() {
        // Форма ответа GigaChat, на котором упала проверка презентации на стенде.
        String answer = """
                ```json
                {"verdict": "partially", "criteria": [
                  {"key": "level", "score": 6, "comment": "Слайды 2–3 объясняют основы"},
                  {"key": "turnoffs", "score": 3, "comment": "Слайд "Наш инструмент ExpLab": прямая реклама"}
                ], "issues": [{"fragmentId": "slide-7", "problem": "Реклама "ExpLab"", "fix": "Убери слайд"}]}
                ```""";

        AudienceFitDraft draft = client.parse(answer, AudienceFitDraft.class);

        assertThat(draft.criteria()).hasSize(2);
        assertThat(draft.criteria().get(1).comment()).isEqualTo("Слайд \"Наш инструмент ExpLab\": прямая реклама");
        assertThat(draft.issues().getFirst().problem()).isEqualTo("Реклама \"ExpLab\"");
    }

    @Test
    void stillClosesTruncatedAnswer() {
        AudienceFitDraft draft = client.parse("{\"verdict\": \"fits\", \"criteria\": [{\"key\": \"level\", \"score\": 9}]", AudienceFitDraft.class);

        assertThat(draft.verdict()).isEqualTo("fits");
        assertThat(draft.criteria()).hasSize(1);
    }

    @Test
    void throwsWhenNothingHelps() {
        assertThatThrownBy(() -> client.parse("{\"criteria\": [1, 2}", AudienceFitDraft.class))
                .isInstanceOf(RuntimeException.class);
    }

    private static SpringAiProducerLlmClient client() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        ProducerProperties properties = new ProducerProperties(3, 3, 4, 365, 80, 12, 3, 0.2, 10, 5, "producer-v2");
        return new SpringAiProducerLlmClient(builder, new ProducerJson(), properties, "GigaChat-2-Max");
    }
}
