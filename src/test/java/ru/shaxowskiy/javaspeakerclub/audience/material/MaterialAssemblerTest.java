package ru.shaxowskiy.javaspeakerclub.audience.material;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceTestData;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MaterialAssemblerTest {

    @Test
    void talkCardGoesFirstAndBlankFieldsAreSkipped() {
        MaterialAssembler assembler = new MaterialAssembler(AudienceTestData.properties());

        MaterialSnapshot snapshot = assembler.assemble(
                AudienceTestData.talk(UUID.randomUUID(), 1L, "   "),
                "slides.pptx",
                List.of(new Fragment("slide-1", "Слайд 1", "Кейс")),
                false
        );

        assertThat(snapshot.fragments()).extracting(Fragment::id).containsExactly("talk-name", "talk-topic", "slide-1");
        assertThat(snapshot.talkName()).isEqualTo("Virtual threads в продакшене");
        assertThat(snapshot.fileName()).isEqualTo("slides.pptx");
        assertThat(snapshot.totalChars()).isEqualTo("Virtual threads в продакшене".length() + "Java".length() + "Кейс".length());
        assertThat(snapshot.truncated()).isFalse();
        assertThat(snapshot.unsupportedFile()).isNull();
    }

    @Test
    void truncatesLongFragmentsAndStopsAtTotalLimit() {
        MaterialAssembler assembler = new MaterialAssembler(AudienceTestData.properties(250, 100, 10));

        MaterialSnapshot snapshot = assembler.assemble(null, "talk.pdf", List.of(
                new Fragment("page-1", "Страница 1", "x".repeat(300)),
                new Fragment("page-2", "Страница 2", "y".repeat(100)),
                new Fragment("page-3", "Страница 3", "z".repeat(100))
        ), false);

        assertThat(snapshot.fragments()).extracting(Fragment::id).containsExactly("page-1", "page-2");
        assertThat(snapshot.fragments().getFirst().text()).hasSize(100).endsWith("…");
        assertThat(snapshot.totalChars()).isEqualTo(200);
        assertThat(snapshot.truncated()).isTrue();
    }

    @Test
    void marksUnsupportedFile() {
        MaterialAssembler assembler = new MaterialAssembler(AudienceTestData.properties());

        MaterialSnapshot snapshot = assembler.assemble(null, "old.ppt", List.of(), true);

        assertThat(snapshot.unsupportedFile()).isEqualTo("old.ppt");
        assertThat(snapshot.fragments()).isEmpty();
    }

    @Test
    void contentHashDependsOnTextOnly() {
        MaterialAssembler assembler = new MaterialAssembler(AudienceTestData.properties());
        List<Fragment> fragments = List.of(new Fragment("slide-1", "Слайд 1", "Кейс"));

        String first = assembler.contentHash(assembler.assemble(null, "a.pptx", fragments, false));
        String sameText = assembler.contentHash(assembler.assemble(null, "b.pptx", fragments, false));
        String otherText = assembler.contentHash(assembler.assemble(null, "a.pptx",
                List.of(new Fragment("slide-1", "Слайд 1", "Другой кейс")), false));

        assertThat(first).hasSize(64).isEqualTo(sameText).isNotEqualTo(otherText);
    }
}
