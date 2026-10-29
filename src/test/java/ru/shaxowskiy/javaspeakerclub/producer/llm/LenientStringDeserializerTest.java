package ru.shaxowskiy.javaspeakerclub.producer.llm;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventAnalysisDraft;

import static org.assertj.core.api.Assertions.assertThat;

class LenientStringDeserializerTest {

    private final ProducerJson json = new ProducerJson();

    @Test
    void joinsListWhereStringIsExpected() {
        // Так отвечает GigaChat: value критерия — список, а не строка.
        EventAnalysisDraft draft = json.read("""
                {"params": {"city": {"value": ["Москва"], "status": "stated", "quote": "в Москве"}},
                 "criteria": [{"text": "Русский язык", "kind": "mandatory", "check": "language",
                               "value": ["русский", "английский"], "status": "stated", "quote": ["на русском"]}]}
                """, EventAnalysisDraft.class);

        assertThat(draft.criteria().getFirst().value()).isEqualTo("русский, английский");
        assertThat(draft.criteria().getFirst().quote()).isEqualTo("на русском");
        assertThat(draft.params().get("city").quote()).isEqualTo("в Москве");
    }

    @Test
    void keepsPlainStringsAndNulls() {
        EventAnalysisDraft draft = json.read("""
                {"criteria": [{"text": "Опыт", "kind": "desirable", "check": "competency", "value": "Android",
                               "status": null, "quote": []}]}
                """, EventAnalysisDraft.class);

        assertThat(draft.criteria().getFirst().value()).isEqualTo("Android");
        assertThat(draft.criteria().getFirst().status()).isNull();
        assertThat(draft.criteria().getFirst().quote()).isNull();
    }
}
