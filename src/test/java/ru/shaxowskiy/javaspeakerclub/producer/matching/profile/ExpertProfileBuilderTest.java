package ru.shaxowskiy.javaspeakerclub.producer.matching.profile;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile.ExpertiseArea;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.TODAY;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.expert;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.feedback;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.preferences;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.talk;

class ExpertProfileBuilderTest {

    @Test
    void separatesConfirmedPotentialAdjacentAndInsufficientExpertise() {
        ExpertFacts facts = expert(1, "Илья", "Москва", "Android Developer", "Пишу на Kotlin", List.of("ios"),
                preferences(List.of("русский"), true, true, "блокчейн"),
                List.of(talk("t1", "Compose в продакшене", 20, "CONFERENCE", "OFFLINE", "android")),
                List.of());
        TagCooccurrence cooccurrence = TagCooccurrence.from(List.of(List.of("android", "gradle"), List.of("android", "gradle")));

        ExpertProfile profile = ExpertProfileBuilder.build(facts, cooccurrence, List.of("android", "kotlin", "gradle"), TODAY);

        assertThat(level(profile, "android")).isEqualTo(ExpertProfile.CONFIRMED);
        assertThat(level(profile, "ios")).isEqualTo(ExpertProfile.POTENTIAL);
        assertThat(level(profile, "kotlin")).isEqualTo(ExpertProfile.POTENTIAL);
        assertThat(level(profile, "gradle")).isEqualTo(ExpertProfile.ADJACENT);
        assertThat(level(profile, "блокчейн")).isEqualTo(ExpertProfile.INSUFFICIENT_DATA);
    }

    @Test
    void reviewsKeepAuthorAndTalkContextAndContradictionsAreVisible() {
        ExpertFacts facts = expert(2, "Тимур", "Москва", null, null, List.of(), preferences(List.of(), null, null),
                List.of(talk("t1", "Outbox на практике", 40, "MEETUP", "ONLINE", "backend")),
                List.of(feedback("f1", "t1", 10, "organizer", "Глубоко", "-"),
                        feedback("f2", "t1", 9, "participant", "Сильно", "-"),
                        feedback("f3", "t1", 5, "participant", "-", "Монотонно"),
                        feedback("f4", "t1", 4, "participant", "-", "Читал со слайдов")));

        ExpertProfile profile = ExpertProfileBuilder.build(facts, TagCooccurrence.empty(), List.of(), TODAY);

        assertThat(profile.feedback().fromOrganizers()).isEqualTo(1);
        assertThat(profile.feedback().contradictory()).isTrue();
        assertThat(profile.feedback().recent()).allSatisfy(review -> assertThat(review.talkTopic()).isEqualTo("Outbox на практике"));
        assertThat(profile.gaps()).extracting(ExpertProfile.Gap::field)
                .contains("about", "companyRole", "languages", "attendance");
        assertThat(profile.dataCompleteness()).isEqualTo("low");
    }

    private static String level(ExpertProfile profile, String area) {
        return profile.expertise().stream()
                .filter(item -> item.area().equals(area))
                .map(ExpertiseArea::level)
                .findFirst()
                .orElse(null);
    }
}
