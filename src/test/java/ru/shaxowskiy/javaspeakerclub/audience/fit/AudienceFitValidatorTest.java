package ru.shaxowskiy.javaspeakerclub.audience.fit;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceTestData;
import ru.shaxowskiy.javaspeakerclub.audience.Rejection;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitDraft.Criterion;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitDraft.Issue;
import ru.shaxowskiy.javaspeakerclub.audience.fit.AudienceFitDraft.Strength;
import ru.shaxowskiy.javaspeakerclub.audience.material.Fragment;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialSnapshot;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AudienceFitValidatorTest {

    private final AudienceFitValidator validator = new AudienceFitValidator(AudienceTestData.properties());

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

        AudienceFitValidator.Result result = validator.validate(draft, material, "high");

        // (4*0.3 + 8*0.25 + 6*0.2 + 5*0.15 + 10*0.1) * 10 = 61.5 → 62
        assertThat(result.result().score()).isEqualTo(62);
        assertThat(result.result().verdict()).isEqualTo(AudienceFitValidator.PARTIALLY);
        assertThat(result.rejections()).extracting(Rejection::item).contains("Вердикт");
        assertThat(result.result().criteria()).extracting(AudienceFitResult.Criterion::label)
                .containsExactly("Уровень сложности", "Тема и роли слушателей", "Ожидания аудитории",
                        "Требуемые знания", "Нет того, что отталкивает");
    }

    @Test
    void scoreUsesOnlyEvaluatedCriteria() {
        AudienceFitDraft draft = draft(null, List.of(
                new Criterion("relevance", "9", null),
                new Criterion("level", 7.4, null),
                new Criterion("charisma", 10, null),
                new Criterion("expectations", "нет", null),
                new Criterion("level", 1, null)
        ));

        AudienceFitValidator.Result result = validator.validate(draft, material, "medium");

        // level 7 (первая оценка), relevance 9: (7*0.3 + 9*0.25) / 0.55 * 10 = 79.1 → 79
        assertThat(result.result().criteria()).extracting(AudienceFitResult.Criterion::key).containsExactly("level", "relevance");
        assertThat(result.result().score()).isEqualTo(79);
        assertThat(result.result().verdict()).isEqualTo(AudienceFitValidator.FITS);
        assertThat(result.rejections()).extracting(Rejection::reason)
                .contains("Неизвестный критерий", "Нет оценки", "Оценка 7.4 приведена к 7");
    }

    @Test
    void clampsScoresIntoRange() {
        AudienceFitDraft draft = draft(null, List.of(new Criterion("level", 15, null), new Criterion("relevance", -3, null)));

        AudienceFitValidator.Result result = validator.validate(draft, material, "high");

        assertThat(result.result().criteria()).extracting(AudienceFitResult.Criterion::score).containsExactly(10, 0);
    }

    @Test
    void verdictThresholds() {
        assertThat(validator.verdict(70)).isEqualTo(AudienceFitValidator.FITS);
        assertThat(validator.verdict(69)).isEqualTo(AudienceFitValidator.PARTIALLY);
        assertThat(validator.verdict(45)).isEqualTo(AudienceFitValidator.PARTIALLY);
        assertThat(validator.verdict(44)).isEqualTo(AudienceFitValidator.NOT_FITS);
    }

    @Test
    void failsWithoutAnyCriterion() {
        AudienceFitDraft draft = draft("fits", List.of(new Criterion("vibe", 9, null)));

        assertThatThrownBy(() -> validator.validate(draft, material, "high"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void issuesAndStrengthsMustPointToExistingFragments() {
        AudienceFitDraft draft = new AudienceFitDraft("partially", "Итог",
                List.of(new Criterion("level", 5, null)),
                List.of(
                        new Issue("SLIDE-1", "Базовое определение потока", "Убери слайд"),
                        new Issue("slide-9", "Выдуманный слайд", "—"),
                        new Issue("slide-1", "Базовое определение потока", "Дубль"),
                        new Issue("slide-2", "  ", null)
                ),
                List.of(
                        new Strength("slide-2", "Реальные цифры из продакшена"),
                        new Strength("page-1", "Нет такого")
                ));

        AudienceFitValidator.Result result = validator.validate(draft, material, "high");

        assertThat(result.result().issues()).singleElement().satisfies(issue -> {
            assertThat(issue.fragmentId()).isEqualTo("slide-1");
            assertThat(issue.label()).isEqualTo("Слайд 1");
            assertThat(issue.quote()).isEqualTo("Что такое поток в Java");
            assertThat(issue.fix()).isEqualTo("Убери слайд");
        });
        assertThat(result.result().strengths()).singleElement()
                .extracting(AudienceFitResult.Strength::quote).isEqualTo("Наш кейс: 40k RPS на virtual threads");
        assertThat(result.rejections()).extracting(Rejection::reason)
                .anyMatch(reason -> reason.contains("slide-9"))
                .anyMatch(reason -> reason.contains("page-1"));
    }

    @Test
    void carriesProfileConfidenceAndTruncation() {
        MaterialSnapshot truncated = new MaterialSnapshot("Доклад", null, material.fragments(), 100, true, null);

        AudienceFitResult result = validator.validate(draft(null, List.of(new Criterion("level", 8, null))), truncated, "low").result();

        assertThat(result.profileConfidence()).isEqualTo("low");
        assertThat(result.truncated()).isTrue();
    }

    private static AudienceFitDraft draft(String verdict, List<Criterion> criteria) {
        return new AudienceFitDraft(verdict, "Итог", criteria, List.of(), List.of());
    }
}
