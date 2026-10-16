package ru.shaxowskiy.javaspeakerclub.producer.matching.analysis;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventAnalysisDraft;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventAnalysisDraft.DraftCriterion;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventAnalysisDraft.DraftParam;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventParamKey;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventState;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.EVENT_ID;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.stateWithDraft;

class EventCardAssemblerTest {

    private static final String DESCRIPTION = "Митап в Москве про мобильную аналитику для тимлидов. "
            + "Спикер обязательно должен иметь опыт A/B-тестов в мобильных приложениях. Доклад 30 минут.";

    @Test
    void keepsOnlyValuesQuotedFromDescriptionAndMarksGuessesAsInferred() {
        EventAnalysisDraft draft = new EventAnalysisDraft(Map.of(
                "city", new DraftParam(List.of("Москва"), "stated", "Митап в Москве"),
                "audienceLevel", new DraftParam(List.of("senior"), "inferred", "для тимлидов"),
                "language", new DraftParam(List.of("английский"), "stated", "доклад на английском")
        ), List.of("mobile", "android"), List.of());

        EventCard card = assemble(stateWithDraft(draft));

        assertThat(card.tags()).containsExactly("mobile");
        assertThat(card.notes()).anyMatch(note -> note.contains("Отброшены теги") && note.contains("android"));

        assertThat(card.param(EventParamKey.CITY).status()).isEqualTo(EventCard.CONFIRMED);
        assertThat(card.param(EventParamKey.AUDIENCE_LEVEL).status()).isEqualTo(EventCard.INFERRED);
        assertThat(card.param(EventParamKey.LANGUAGE).status()).isEqualTo(EventCard.UNKNOWN);
        assertThat(card.notes()).anyMatch(note -> note.contains("Отброшены значения") && note.contains("английский"));
    }

    @Test
    void unknownParamsGoLastAndQuestionsAskOnlyForCriticalOnes() {
        EventAnalysisDraft draft = new EventAnalysisDraft(Map.of(
                "city", new DraftParam(List.of("Москва"), "stated", "Митап в Москве")
        ), List.of(), List.of());

        EventCard card = assemble(stateWithDraft(draft));

        assertThat(card.params().getFirst().key()).isEqualTo("city");
        assertThat(card.params().getLast().status()).isEqualTo(EventCard.UNKNOWN);
        assertThat(card.questions()).hasSizeLessThanOrEqualTo(4)
                .extracting(EventCard.Question::key)
                .contains("topics", "audienceLevel")
                .doesNotContain("city", "goal");
    }

    @Test
    void mandatoryWithoutExplicitWordsIsInterpretation() {
        EventAnalysisDraft draft = new EventAnalysisDraft(Map.of(), List.of(), List.of(
                new DraftCriterion("Опыт A/B-тестов", "mandatory", "competency", "A/B-тесты", "stated",
                        "Спикер обязательно должен иметь опыт A/B-тестов"),
                new DraftCriterion("Опыт с тимлидами", "mandatory", "audience", "тимлиды", "stated", "для тимлидов")
        ));

        EventCard card = assemble(stateWithDraft(draft));

        assertThat(card.criteria()).extracting(EventCard.Criterion::text, EventCard.Criterion::interpretation)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("Опыт A/B-тестов", false),
                        org.assertj.core.groups.Tuple.tuple("Опыт с тимлидами", true));
    }

    @Test
    void organizerValueWinsOverDescriptionAndAnswerFillsQuestion() {
        EventAnalysisDraft draft = new EventAnalysisDraft(Map.of(
                "city", new DraftParam(List.of("Москва"), "stated", "Митап в Москве")
        ), List.of(), List.of());
        Map<String, List<String>> structured = new LinkedHashMap<>(Map.of("city", List.of("Казань")));
        Map<String, List<String>> answers = new LinkedHashMap<>(Map.of(
                "attendance", List.of("только офлайн"),
                "audienceLevel", List.of("что-то непонятное")));
        EventState state = new EventState(structured, answers, new LinkedHashSet<>(Set.of("talkType")), List.of(),
                new LinkedHashSet<>(), draft, List.of(), null, null);

        EventCard card = assemble(state);

        assertThat(card.value(EventParamKey.CITY)).isEqualTo("Казань");
        assertThat(card.notes()).anyMatch(note -> note.contains("Используется значение организатора"));
        assertThat(card.value(EventParamKey.ATTENDANCE)).isEqualTo("offline");
        assertThat(question(card, "attendance").status()).isEqualTo(EventCardAssembler.QUESTION_ANSWERED);
        assertThat(question(card, "audienceLevel").status()).isEqualTo(EventCardAssembler.QUESTION_SKIPPED);
        assertThat(question(card, "talkType").status()).isEqualTo(EventCardAssembler.QUESTION_SKIPPED);
    }

    @Test
    void showsChangedRequirementsSinceLastMatching() {
        EventState matched = new EventState(new LinkedHashMap<>(Map.of("topics", List.of("Android"))), new LinkedHashMap<>(),
                new LinkedHashSet<>(), List.of(), new LinkedHashSet<>(), null, List.of(),
                Map.of("topics", List.of("Android")), List.of());
        EventState changed = new EventState(new LinkedHashMap<>(Map.of("topics", List.of("Android"), "city", List.of("Москва"))),
                new LinkedHashMap<>(), new LinkedHashSet<>(),
                List.of(new EventState.CriterionInput("Опыт Kotlin", "mandatory", "competency", "kotlin")),
                new LinkedHashSet<>(), null, List.of(), matched.matchedParams(), matched.matchedCriteria());

        EventCard card = assemble(changed);

        assertThat(card.changesSinceLastMatching())
                .anyMatch(change -> change.startsWith("География (город): не указано → Москва"))
                .anyMatch(change -> change.contains("добавлено требование «обязательно: Опыт Kotlin»"));
    }

    private static EventCard assemble(EventState state) {
        return EventCardAssembler.assemble(EVENT_ID, "Mobile Day", DESCRIPTION, EventCard.STATUS_ANALYZED, state,
                List.of("mobile", "analytics", "android"), 4, LocalDateTime.of(2026, 10, 16, 12, 0));
    }

    private static EventCard.Question question(EventCard card, String key) {
        return card.questions().stream().filter(question -> question.key().equals(key)).findFirst().orElseThrow();
    }
}
