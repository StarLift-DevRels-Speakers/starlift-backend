package ru.shaxowskiy.javaspeakerclub.producer.matching.service;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation.TalkRef;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.RecommendationSummaryWriter.Draft;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.RecommendationSummaryWriter.DraftItem;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.RecommendationSummaryWriter.Input;
import ru.shaxowskiy.javaspeakerclub.producer.matching.service.RecommendationSummaryWriter.Result;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationSummaryWriterTest {

    private final List<Input> inputs = List.of(
            new Input("1", "Илья", List.of("Подтверждённый опыт: 2 доклада, последний — 26.09.2026."),
                    List.of(new TalkRef("t1", "Jetpack Compose", "Mobile Ural", LocalDate.of(2026, 9, 26), "офлайн", List.of("android")))),
            new Input("2", "Мария", List.of("Опыт заявлен, но не подтверждён выступлениями."), List.of()),
            new Input("3", "Олег", List.of("Есть критический отзыв."), List.of()),
            new Input("4", "Павел", List.of("Опыт по теме старый."), List.of()));

    @Test
    void acceptsGroundedSummaryAndRejectsInventions() {
        Draft draft = new Draft(List.of(
                new DraftItem("1", "Два свежих доклада по Android, последний 26.09.2026 — опыт подтверждён.", List.of("t1")),
                new DraftItem("2", "Выступала на 5 конференциях по iOS.", List.of()),
                new DraftItem("3", "Хороший кандидат, см. доклад.", List.of("t1")),
                new DraftItem("4", "候选人 опыт старый", List.of()),
                new DraftItem("99", "Неизвестный кандидат", List.of())));

        Result result = RecommendationSummaryWriter.validate(draft, inputs);

        assertThat(result.summaries()).containsOnlyKeys("1");
        assertThat(result.report().rejected())
                .anyMatch(reason -> reason.startsWith("Мария") && reason.contains("число 5"))
                .anyMatch(reason -> reason.startsWith("Олег") && reason.contains("доклад"))
                .anyMatch(reason -> reason.startsWith("Павел") && reason.contains("не на русском"))
                .anyMatch(reason -> reason.contains("неизвестного варианта 99"));
    }

    @Test
    void verdictInsteadOfFactsIsRejected() {
        Result verdict = RecommendationSummaryWriter.validate(new Draft(List.of(
                new DraftItem("2", "Мария — идеальный кандидат для митапа.", List.of()))), inputs);

        assertThat(verdict.summaries()).isEmpty();
        assertThat(verdict.report().rejected()).singleElement().asString().contains("оценка вместо фактов");
    }

    @Test
    void limitationMustComeFromFacts() {
        List<Input> plusOnly = List.of(new Input("5", "Анна", List.of("Плюс: Живёт в городе мероприятия: Москва."), List.of()));

        Result result = RecommendationSummaryWriter.validate(new Draft(List.of(
                new DraftItem("5", "Опыт подтверждён, но она живёт в Москве, что может быть ограничением.", List.of()))), plusOnly);

        assertThat(result.summaries()).isEmpty();
        assertThat(result.report().rejected()).singleElement().asString().contains("ограничение, которого нет в фактах");
    }

    @Test
    void organizerSummaryMustNotAddressTheSpeaker() {
        Draft draft = new Draft(List.of(new DraftItem("1", "Тема совпадает с твоими докладами по Android.", List.of("t1"))));

        assertThat(RecommendationSummaryWriter.validate(draft, inputs, RecommendationSummaryWriter.AUDIENCE_ORGANIZER).summaries())
                .isEmpty();
        assertThat(RecommendationSummaryWriter.validate(draft, inputs, RecommendationSummaryWriter.AUDIENCE_EXPERT).summaries())
                .containsOnlyKeys("1");
    }

    @Test
    void emptyOrBrokenAnswerGivesNoSummaries() {
        assertThat(RecommendationSummaryWriter.validate(null, inputs).summaries()).isEmpty();
        assertThat(RecommendationSummaryWriter.validate(new Draft(List.of(new DraftItem("1", "<пояснение>", List.of()))), inputs)
                .summaries()).isEmpty();
    }
}
