package ru.shaxowskiy.javaspeakerclub.producer.validation;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerProperties;
import ru.shaxowskiy.javaspeakerclub.producer.model.PlanValidation;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlan;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft.Evidence;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft.Step;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft.Theme;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProducerPlanValidatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 27);

    private final ProducerPlanValidator validator = new ProducerPlanValidator(
            new ProducerProperties(3, 3, 4, 365, 80, 12, 3, 0.2, 10, "producer-v1")
    );

    @Test
    void acceptsThemeConfirmedByEnoughFeedbackAndCountsOnlyKnownIds() {
        ProducerPlanDraft draft = draft(
                List.of(new Theme("Живые кейсы", List.of("f1", "f2", "f3", "ghost"))),
                List.of(),
                List.of()
        );

        var result = validator.validate(draft, snapshot(), TODAY);

        ProducerPlan.Theme theme = result.plan().strengths().getFirst();
        assertThat(theme.mentions()).isEqualTo(3);
        assertThat(theme.feedbackIds()).containsExactly("f1", "f2", "f3");
        assertThat(theme.quotes()).extracting(ProducerPlan.Quote::text)
                .containsExactly("Реальные цифры из продакшена", "Кейсы вместо теории");
        assertThat(result.validation().rejections())
                .extracting(PlanValidation.Rejection::reason)
                .anyMatch(reason -> reason.contains("которых нет в данных спикера: 1"));
    }

    @Test
    void rejectsThemeBelowMentionThreshold() {
        ProducerPlanDraft draft = draft(
                List.of(),
                List.of(new Theme("Мало времени на вопросы", List.of("f4", "f5"))),
                List.of()
        );

        var result = validator.validate(draft, snapshot(), TODAY);

        assertThat(result.plan().growthAreas()).isEmpty();
        assertThat(result.validation().rejections().getFirst().reason()).contains("Подтверждена отзывами: 2");
    }

    @Test
    void growthQuotesUseNegativeTextAndFallBackToImprovement() {
        ProducerPlanDraft draft = draft(
                List.of(),
                List.of(new Theme("Мало времени на вопросы", List.of("f6", "f4", "f5"))),
                List.of()
        );

        var result = validator.validate(draft, snapshot(), TODAY);

        assertThat(result.plan().growthAreas().getFirst().quotes()).extracting(ProducerPlan.Quote::text)
                .containsExactly("Оставить 10 минут на Q&A", "Не успели задать вопросы");
    }

    @Test
    void rejectsStepWithoutKnownEvidence() {
        ProducerPlanDraft draft = draft(List.of(), List.of(), List.of(
                new Step("Выступить на митапе", "Давно не было выступлений", null,
                        List.of(new Evidence("TALK", "no-such-talk"), new Evidence("MAGIC", "t1")))
        ));

        var result = validator.validate(draft, snapshot(), TODAY);

        assertThat(result.plan().steps()).isEmpty();
        assertThat(result.validation().rejections()).extracting(PlanValidation.Rejection::reason)
                .contains("Нет ни одного подтверждённого доказательства");
    }

    @Test
    void clampsDueDateToCfpDeadlineAndBuildsLabelsFromSnapshot() {
        ProducerPlanDraft draft = draft(List.of(), List.of(), List.of(
                new Step("Подать заявку на Mobile Frontier", "Уровень выше текущего", "2026-12-31",
                        List.of(new Evidence("cfp", "c1"), new Evidence("TALK", "t1")))
        ));

        var result = validator.validate(draft, snapshot(), TODAY);

        ProducerPlan.Step step = result.plan().steps().getFirst();
        assertThat(step.dueDate()).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(step.evidence()).extracting(ProducerPlan.EvidenceRef::label)
                .containsExactly("CFP «Mobile Frontier», дедлайн 20.10.2026", "Доклад «A/B-тесты без боли», 01.09.2026");
        assertThat(result.validation().rejections()).extracting(PlanValidation.Rejection::reason)
                .anyMatch(reason -> reason.startsWith("Срок сдвинут до дедлайна CFP"));
    }

    @Test
    void dropsCfpWithPassedDeadlineAndPastDueDates() {
        ProducerPlanDraft draft = draft(List.of(), List.of(), List.of(
                new Step("Подать на прошедший CFP", "…", "2026-01-01",
                        List.of(new Evidence("CFP", "c-past"), new Evidence("FEEDBACK", "f4")))
        ));

        var result = validator.validate(draft, snapshot(), TODAY);

        ProducerPlan.Step step = result.plan().steps().getFirst();
        assertThat(step.evidence()).extracting(ProducerPlan.EvidenceRef::id).containsExactly("f4");
        assertThat(step.dueDate()).isNull();
    }

    @Test
    void keepsAtMostMaxSteps() {
        Step step = new Step("Шаг", "…", null, List.of(new Evidence("FEEDBACK", "f1")));
        ProducerPlanDraft draft = draft(List.of(), List.of(), List.of(
                step, rename(step, "Шаг 2"), rename(step, "Шаг 3"), rename(step, "Шаг 4")
        ));

        var result = validator.validate(draft, snapshot(), TODAY);

        assertThat(result.plan().steps()).hasSize(3);
        assertThat(result.validation().rejections()).extracting(PlanValidation.Rejection::item)
                .contains("Шаг «Шаг 4»");
    }

    @Test
    void survivesNullsFromLlm() {
        var result = validator.validate(new ProducerPlanDraft(null, null, null, null), snapshot(), TODAY);

        assertThat(result.plan().summary()).isEqualTo("План собран по данным спикера.");
        assertThat(result.plan().stage().current()).isEqualTo("federal");
        assertThat(result.validation().acceptedSteps()).isZero();
    }

    private static Step rename(Step step, String title) {
        return new Step(title, step.why(), step.dueDate(), step.evidence());
    }

    private static ProducerPlanDraft draft(List<Theme> strengths, List<Theme> growth, List<Step> steps) {
        return new ProducerPlanDraft("Растёшь, пора на международный уровень.", strengths, growth, steps);
    }

    private static SpeakerSnapshot snapshot() {
        return new SpeakerSnapshot(
                TODAY,
                new SpeakerSnapshot.Speaker(1L, "anna", "Анна Левина", "Aurora", null, "Москва",
                        List.of("Product Evangelist"), "federal", "international"),
                new SpeakerSnapshot.Activity(8, 3, 2, LocalDate.of(2026, 9, 1), 26, "growing"),
                new SpeakerSnapshot.Nps(88, 82, 6),
                List.of(new SpeakerSnapshot.TalkItem("t1", "A/B-тесты без боли", LocalDate.of(2026, 9, 1),
                        "ProductCamp", "CONFERENCE", "OFFLINE", List.of("product"))),
                List.of(
                        feedback("f1", 10, "Реальные цифры из продакшена", "-", "-"),
                        feedback("f2", 9, "Кейсы вместо теории", "Замечаний нет", "-"),
                        feedback("f3", 9, "Живые примеры", "—", "-"),
                        feedback("f4", 7, "Интересно", "Не успели задать вопросы", "Больше времени на вопросы"),
                        feedback("f5", 6, "Тема важная", "Q&A обрезали", "-"),
                        feedback("f6", 7, "Понятно", "-", "Оставить 10 минут на Q&A")
                ),
                new SpeakerSnapshot.FeedbackStats(6, 8.0, 50, 17, 33),
                List.of(
                        new SpeakerSnapshot.CfpItem("c1", "Mobile Frontier", "Санкт-Петербург", "international",
                                "Product", LocalDate.of(2026, 10, 20), 89, List.of("product"), "https://example.com/c1"),
                        new SpeakerSnapshot.CfpItem("c-past", "Old Conf", "Москва", "federal",
                                "Product", LocalDate.of(2026, 9, 1), 70, List.of(), "https://example.com/c2")
                )
        );
    }

    private static SpeakerSnapshot.FeedbackItem feedback(String id, int rating, String positive, String negative, String improvement) {
        return new SpeakerSnapshot.FeedbackItem(id, "t1", rating, positive, negative, improvement, LocalDate.of(2026, 9, 2));
    }
}
