package ru.shaxowskiy.javaspeakerclub.producer.llm;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRepairTest {

    @Test
    void closesMissingBracketsAtTheEnd() {
        assertThat(JsonRepair.closeUnbalanced("Ответ: {\"items\": [{\"id\": \"1\", \"summary\": \"скобка } в тексте\"}]"))
                .isEqualTo("{\"items\": [{\"id\": \"1\", \"summary\": \"скобка } в тексте\"}]}");
    }

    @Test
    void keepsCompleteObjectAndCutsTrailingText() {
        assertThat(JsonRepair.closeUnbalanced("```json\n{\"a\": [1, 2]}\n```")).isEqualTo("{\"a\": [1, 2]}");
    }

    @Test
    void givesUpOnBrokenStructure() {
        assertThat(JsonRepair.closeUnbalanced("{\"a\": [1, 2}")).isNull();
        assertThat(JsonRepair.closeUnbalanced("{\"a\": \"не закрыта")).isNull();
        assertThat(JsonRepair.closeUnbalanced("нет json")).isNull();
    }

    @Test
    void escapesUnescapedQuotesInsideValues() {
        String broken = "{\"criteria\": [{\"key\": \"turnoffs\", \"score\": 3, "
                + "\"comment\": \"Слайд \"Наш инструмент ExpLab\": реклама\"}, {\"key\": \"level\", \"score\": 6}]}";

        assertThat(JsonRepair.escapeInnerQuotes(broken)).isEqualTo("{\"criteria\": [{\"key\": \"turnoffs\", \"score\": 3, "
                + "\"comment\": \"Слайд \\\"Наш инструмент ExpLab\\\": реклама\"}, {\"key\": \"level\", \"score\": 6}]}");
    }

    @Test
    void escapesQuotesFollowedByCommaInsideText() {
        String broken = "{\"why\": \"Он сказал \"привет\", потом ушёл\", \"items\": [\"a\", \"b\"]}";

        assertThat(JsonRepair.escapeInnerQuotes(broken))
                .isEqualTo("{\"why\": \"Он сказал \\\"привет\\\", потом ушёл\", \"items\": [\"a\", \"b\"]}");
    }

    @Test
    void leavesValidJsonUntouched() {
        String valid = "{\"a\": \"с \\\"экраном\\\"\", \"b\": [1, {\"c\": null}], \"d\": \"x\"}";

        assertThat(JsonRepair.escapeInnerQuotes(valid)).isEqualTo(valid);
        assertThat(JsonRepair.escapeInnerQuotes("нет json")).isNull();
    }
}
