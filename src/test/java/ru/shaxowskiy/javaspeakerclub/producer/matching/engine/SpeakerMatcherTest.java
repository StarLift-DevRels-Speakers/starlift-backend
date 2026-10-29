package ru.shaxowskiy.javaspeakerclub.producer.matching.engine;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventState.CriterionInput;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.SpeakerMatchResult.Candidate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.TODAY;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.card;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.expert;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.feedback;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.preferences;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.profiles;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.talk;

class SpeakerMatcherTest {

    private static final List<String> VOCABULARY = List.of("android", "kotlin", "mobile", "backend");

    private final ExpertFacts ilya = expert(1, "Илья", "Москва", "Android Developer", "Android и Kotlin", List.of("android"),
            preferences(List.of("русский"), true, true, "android"),
            List.of(talk("t1", "Jetpack Compose в продакшене", 20, "CONFERENCE", "OFFLINE", "android", "kotlin"),
                    talk("t2", "Утечки памяти на Android", 60, "MEETUP", "OFFLINE", "android")),
            List.of(feedback("f1", "t1", 10, "organizer", "Лучший доклад дня", "-"),
                    feedback("f2", "t2", 9, "participant", "Живое демо", "-")));

    private final ExpertFacts maria = expert(2, "Мария", "Москва", "Mobile Team Lead", "Руковожу Android-командой", List.of("android"),
            preferences(List.of("русский"), true, true), List.of(), List.of());

    private final ExpertFacts timur = expert(3, "Тимур", "Москва", "Backend Lead", null, List.of(),
            preferences(List.of("русский"), true, true),
            List.of(talk("t3", "Распределённые транзакции", 30, "MEETUP", "OFFLINE", "backend")), List.of());

    private final ExpertFacts john = expert(4, "John", "Москва", "Android Engineer", null, List.of(),
            preferences(List.of("английский"), true, true),
            List.of(talk("t4", "Android performance", 40, "CONFERENCE", "OFFLINE", "android")), List.of());

    @Test
    void confirmedExperienceRanksAboveClaimedAndUnrelatedIsNotRecommended() {
        EventCard event = card(Map.of("topics", List.of("Android"), "city", List.of("Москва")), List.of());

        SpeakerMatcher.Outcome outcome = SpeakerMatcher.match(event, List.of(maria, ilya, timur), profiles(List.of(maria, ilya, timur), VOCABULARY),
                TODAY, 10);

        List<Candidate> ranked = outcome.groups().stream().flatMap(group -> group.items().stream()).toList();
        assertThat(ranked).extracting(Candidate::name).containsExactly("Илья", "Мария");
        assertThat(outcome.groups().getFirst().code()).isEqualTo(Recommendation.GROUP_HIGH);
        Candidate ilyaCandidate = ranked.getFirst();
        assertThat(ilyaCandidate.whyFits()).anyMatch(reason -> reason.startsWith("Подтверждённый опыт"));
        assertThat(ilyaCandidate.relevantTalks()).isNotEmpty();
        Candidate mariaCandidate = ranked.get(1);
        assertThat(mariaCandidate.limitations()).anyMatch(reason -> reason.contains("заявлен, но не подтверждён"));
        assertThat(mariaCandidate.confidence().level()).isNotEqualTo(Recommendation.CONFIDENCE_HIGH);
    }

    @Test
    void mandatoryLanguageExcludesWithReasonAndUnknownLanguageOnlyLowersConfidence() {
        ExpertFacts noLanguages = expert(5, "Пётр", "Москва", "Android Dev", "Android", List.of(),
                preferences(List.of(), true, true),
                List.of(talk("t5", "Android-архитектура", 15, "CONFERENCE", "OFFLINE", "android")), List.of());
        EventCard event = card(Map.of("topics", List.of("Android"), "language", List.of("русский")),
                List.of(new CriterionInput("Доклад на русском", "mandatory", "language", "русский")));
        List<ExpertFacts> experts = List.of(ilya, john, noLanguages);

        SpeakerMatcher.Outcome outcome = SpeakerMatcher.match(event, experts, profiles(experts, VOCABULARY), TODAY, 10);

        assertThat(outcome.excluded()).singleElement().satisfies(excluded -> {
            assertThat(excluded.name()).isEqualTo("John");
            assertThat(excluded.reasons()).anyMatch(reason -> reason.contains("Доклад на русском"));
        });
        Candidate petr = outcome.groups().stream().flatMap(group -> group.items().stream())
                .filter(candidate -> candidate.name().equals("Пётр")).findFirst().orElseThrow();
        assertThat(petr.missingData()).anyMatch(item -> item.contains("Не проверено обязательное требование"));
        assertThat(outcome.groups().stream().filter(group -> group.code().equals(Recommendation.GROUP_HIGH))
                .flatMap(group -> group.items().stream()).map(Candidate::name)).doesNotContain("Пётр");
    }

    @Test
    void singleCriticalReviewDoesNotExcludeAndIsExplained() {
        ExpertFacts critic = expert(6, "Олег", "Москва", "Android Dev", null, List.of(), preferences(List.of("русский"), true, true),
                List.of(talk("t6", "Android и CI", 25, "MEETUP", "OFFLINE", "android")),
                List.of(feedback("f6", "t6", 3, "participant", "-", "Скучно и монотонно")));
        EventCard event = card(Map.of("topics", List.of("Android")), List.of());

        SpeakerMatcher.Outcome outcome = SpeakerMatcher.match(event, List.of(critic), profiles(List.of(critic), VOCABULARY), TODAY, 10);

        assertThat(outcome.excluded()).isEmpty();
        Candidate candidate = outcome.groups().getFirst().items().getFirst();
        assertThat(candidate.limitations()).anyMatch(reason -> reason.contains("Один отзыв не исключает кандидата"));
        assertThat(candidate.feedbackInfluence()).contains("Отзывы по другим темам на подбор не влияли");
    }

    @Test
    void withoutTopicsNothingIsMatchedAndOrganizerIsTold() {
        EventCard event = card(Map.of("city", List.of("Москва")), List.of());

        SpeakerMatcher.Outcome outcome = SpeakerMatcher.match(event, List.of(ilya), profiles(List.of(ilya), VOCABULARY), TODAY, 10);

        assertThat(outcome.groups()).isEmpty();
        assertThat(outcome.notes()).anyMatch(note -> note.contains("подбор невозможен"));
    }
}
