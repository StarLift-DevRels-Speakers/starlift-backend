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

    @Test
    void visualStatsOverAllSlidesAndImageOnlySlidesDoNotCountAsText() {
        MaterialAssembler assembler = new MaterialAssembler(AudienceTestData.properties());
        String wall = "слово ".repeat(90).trim();

        MaterialSnapshot snapshot = assembler.assemble(null, "deck.pptx", List.of(
                new Fragment("slide-1", "Слайд 1", "Схема архитектуры", 2),
                new Fragment("slide-2", "Слайд 2", Fragment.IMAGE_ONLY_TEXT, 1),
                new Fragment("slide-3", "Слайд 3", wall, 0),
                new Fragment("slide-4", "Слайд 4", "Выводы", 0)
        ), false);

        assertThat(snapshot.totalChars()).isEqualTo("Схема архитектуры".length() + wall.length() + "Выводы".length());
        MaterialSnapshot.VisualStats visual = snapshot.visual();
        assertThat(visual.slides()).isEqualTo(4);
        assertThat(visual.slidesWithImages()).isEqualTo(2);
        assertThat(visual.imageOnlySlides()).isEqualTo(1);
        assertThat(visual.textOnlySlides()).containsExactly("Слайд 3", "Слайд 4");
        assertThat(visual.wallOfTextSlides()).containsExactly("Слайд 3");
        assertThat(visual.averageWords()).isEqualTo(Math.round((2 + 90 + 1) / 3f));
        assertThat(visual.textOnlyShare()).isEqualTo(0.5);
    }

    @Test
    void noVisualStatsWithoutSlides() {
        MaterialAssembler assembler = new MaterialAssembler(AudienceTestData.properties());

        assertThat(assembler.assemble(AudienceTestData.talk(UUID.randomUUID(), 1L, "Описание"), null, List.of(), false).visual()).isNull();
        assertThat(assembler.assemble(null, "speech.txt", List.of(new Fragment("part-1", "Фрагмент 1", "Текст")), false).visual()).isNull();
    }

    @Test
    void contentHashChangesWhenPicturesAdded() {
        MaterialAssembler assembler = new MaterialAssembler(AudienceTestData.properties());

        String without = assembler.contentHash(assembler.assemble(null, "a.pptx", List.of(new Fragment("slide-1", "Слайд 1", "Кейс", 0)), false));
        String with = assembler.contentHash(assembler.assemble(null, "a.pptx", List.of(new Fragment("slide-1", "Слайд 1", "Кейс", 1)), false));

        assertThat(with).isNotEqualTo(without);
    }
}
