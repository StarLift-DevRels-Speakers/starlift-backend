package ru.shaxowskiy.javaspeakerclub.audience.fit;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceTestData;
import ru.shaxowskiy.javaspeakerclub.audience.Rejection;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitDraft.Criterion;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitDraft.Issue;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitDraft.Strength;
import ru.shaxowskiy.javaspeakerclub.audience.material.Fragment;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialSnapshot;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialSnapshot.VisualStats;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudienceFitValidatorTest {

    private final AudienceFitValidator validator = new AudienceFitValidator(AudienceTestData.properties());

    private final EventAudienceProfile practitioners = AudienceTestData.profile("practitioners", "Senior Java-разработчики", "medium", "high");
    private final EventAudienceProfile students = AudienceTestData.profile("students", "Студенты IT-направлений", "high", "medium");

    private final MaterialSnapshot material = AudienceTestData.material(
            new Fragment("talk-name", "Название доклада", "Virtual threads в продакшене"),
            new Fragment("slide-1", "Слайд 1", "Что такое поток в Java"),
            new Fragment("slide-2", "Слайд 2", "Наш кейс: 40k RPS на virtual threads")
    );

    @Test
    void computesWeightedScoreAndVerdict() {
        AudienceFitDraft draft = draft("fits", List.of(
                new Criterion("level", 4, "Слишком базово"),
                new Criterion("relevance", 8, null),
                new Criterion("expectations", 6, null),
                new Criterion("knowledge", 5, null),
                new Criterion("turnoffs", 10, null)
        ));

        AudienceFitValidator.Result result = validator.validate(draft, material, practitioners);

        // без слайдов «подача» не оценивается: (4*0.25 + 8*0.2 + 6*0.15 + 5*0.15 + 10*0.1) / 0.85 * 10 = 61.8 → 62
        assertThat(result.result().score()).isEqualTo(62);
        assertThat(result.result().verdict()).isEqualTo(AudienceFitValidator.PARTIALLY);
        assertThat(result.result().primarySegment()).isEqualTo("practitioners");
        assertThat(result.result().segmentLabel()).isEqualTo("Senior Java-разработчики");
        assertThat(result.rejections()).extracting(Rejection::item).contains("Вердикт");
    }

    @Test
    void presentationIsDroppedWithoutSlides() {
        AudienceFitDraft draft = draft(null, List.of(new Criterion("level", 8, null), new Criterion("presentation", 2, null)));

        AudienceFitValidator.Result result = validator.validate(draft, material, practitioners);

        assertThat(result.result().criteria()).extracting(AudienceFitResult.Criterion::key).containsExactly("level");
        assertThat(result.result().visual()).isNull();
        assertThat(result.rejections()).extracting(Rejection::reason).contains("Снят: у материала нет слайдов");
    }

    @Test
    void textOnlyDeckIsCappedForAudienceThatNeedsVisuals() {
        MaterialSnapshot deck = deck(new VisualStats(5, 1, 0, 40, List.of("Слайд 1", "Слайд 2", "Слайд 3", "Слайд 4"), List.of()));
        AudienceFitDraft draft = draft(null, List.of(new Criterion("level", 8, null), new Criterion("presentation", 8, "Аккуратно")));

        AudienceFitValidator.Result result = validator.validate(draft, deck, students);

        assertThat(result.result().criteria()).filteredOn(item -> item.key().equals("presentation"))
                .singleElement().extracting(AudienceFitResult.Criterion::score).isEqualTo(4);
        assertThat(result.result().issues().getFirst()).satisfies(issue -> {
            assertThat(issue.fragmentId()).isEqualTo("slide-1");
            assertThat(issue.label()).isEqualTo("Слайд 1, Слайд 2, Слайд 3, Слайд 4");
            assertThat(issue.problem()).contains("4 из 5").contains("Студенты IT-направлений");
        });
        assertThat(result.result().visual().slides()).isEqualTo(5);
    }

    @Test
    void sameDeckIsFineForExperts() {
        MaterialSnapshot deck = deck(new VisualStats(5, 1, 0, 40, List.of("Слайд 1", "Слайд 2", "Слайд 3", "Слайд 4"), List.of("Слайд 2")));
        EventAudienceProfile experts = AudienceTestData.profile("experts", "Архитекторы", "low", "high");

        AudienceFitValidator.Result result = validator.validate(
                draft(null, List.of(new Criterion("level", 8, null), new Criterion("presentation", 8, null))), deck, experts);

        assertThat(result.result().criteria()).filteredOn(item -> item.key().equals("presentation"))
                .singleElement().extracting(AudienceFitResult.Criterion::score).isEqualTo(8);
        assertThat(result.result().issues()).isEmpty();
    }

    @Test
    void wallOfTextIsReportedForPractitioners() {
        MaterialSnapshot deck = deck(new VisualStats(4, 3, 0, 60, List.of("Слайд 4"), List.of("Слайд 2")));

        AudienceFitValidator.Result result = validator.validate(draft(null, List.of(new Criterion("level", 8, null))), deck, practitioners);

        assertThat(result.result().issues()).singleElement().satisfies(issue -> {
            assertThat(issue.fragmentId()).isEqualTo("slide-2");
            assertThat(issue.problem()).contains("Слишком много текста");
        });
        // модель не оценила подачу — сервер посчитал по доле слайдов с картинками: 3 из 4 → 8
        assertThat(result.result().criteria()).filteredOn(item -> item.key().equals("presentation"))
                .singleElement().extracting(AudienceFitResult.Criterion::score).isEqualTo(8);
    }

    @Test
    void scoreUsesOnlyEvaluatedCriteria() {
        AudienceFitDraft draft = draft(null, List.of(
                new Criterion("relevance", "9", null),
                new Criterion("level", 7.4, null),
                new Criterion("charisma", 10, null),
                new Criterion("expectations", "нет", null)
        ));

        AudienceFitValidator.Result result = validator.validate(draft, material, practitioners);

        // level 7, relevance 9: (7*0.25 + 9*0.2) / 0.45 * 10 = 78.9 → 79
        assertThat(result.result().score()).isEqualTo(79);
        assertThat(result.rejections()).extracting(Rejection::reason)
                .contains("Неизвестный критерий", "Нет оценки", "Оценка 7.4 приведена к 7");
    }

    @Test
    void verdictThresholdsAndEmptyCriteria() {
        assertThat(validator.verdict(70)).isEqualTo(AudienceFitValidator.FITS);
        assertThat(validator.verdict(45)).isEqualTo(AudienceFitValidator.PARTIALLY);
        assertThat(validator.verdict(44)).isEqualTo(AudienceFitValidator.NOT_FITS);
        assertThatThrownBy(() -> validator.validate(draft("fits", List.of(new Criterion("vibe", 9, null))), material, practitioners))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void issuesAndStrengthsMustPointToExistingFragments() {
        AudienceFitDraft draft = new AudienceFitDraft("partially", "Итог",
                List.of(new Criterion("level", 5, null)),
                List.of(new Issue("SLIDE-1", "Базовое определение потока", "Убери слайд"), new Issue("slide-9", "Выдуманный слайд", null)),
                List.of(new Strength("slide-2", "Реальные цифры"), new Strength("page-1", "Нет такого")));

        AudienceFitValidator.Result result = validator.validate(draft, material, practitioners);

        assertThat(result.result().issues()).singleElement().satisfies(issue -> {
            assertThat(issue.label()).isEqualTo("Слайд 1");
            assertThat(issue.quote()).isEqualTo("Что такое поток в Java");
        });
        assertThat(result.result().strengths()).singleElement().extracting(AudienceFitResult.Strength::fragmentId).isEqualTo("slide-2");
    }

    private MaterialSnapshot deck(VisualStats visual) {
        List<Fragment> slides = List.of(
                new Fragment("slide-1", "Слайд 1", "Что такое поток", 0),
                new Fragment("slide-2", "Слайд 2", "Очень длинный текст", 0),
                new Fragment("slide-3", "Слайд 3", "Ещё текст", 0),
                new Fragment("slide-4", "Слайд 4", "Выводы", 0),
                new Fragment("slide-5", "Слайд 5", "Схема", 1)
        );
        return new MaterialSnapshot("Доклад", "deck.pptx", slides, 100, false, null, visual);
    }

    private static AudienceFitDraft draft(String verdict, List<Criterion> criteria) {
        return new AudienceFitDraft(verdict, "Итог", criteria, List.of(), List.of());
    }
}
