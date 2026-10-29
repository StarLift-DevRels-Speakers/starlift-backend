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
}
