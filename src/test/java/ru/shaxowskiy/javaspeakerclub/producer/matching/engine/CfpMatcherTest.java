package ru.shaxowskiy.javaspeakerclub.producer.matching.engine;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.producer.matching.engine.CfpMatcher.CfpFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventRecommendationResult.Event;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.TODAY;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.expert;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.preferences;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.profile;
import static ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingFixtures.talk;

class CfpMatcherTest {

    private final ExpertFacts anna = expert(1, "Анна", "Москва", "Product Analyst", null, List.of(),
            preferences(List.of("русский"), true, true, "мобильная аналитика"),
            List.of(talk("t1", "Аналитика для мобильных команд", 30, "CONFERENCE", "OFFLINE", "mobile", "analytics"),
                    talk("t2", "Продуктовые метрики", 90, "MEETUP", "ONLINE", "product")),
            List.of());

    @Test
    void checksLanguageFormatAndRequirementsWhenCfpHasThem() {
        CfpFact full = new CfpFact("c1", "Mobile Frontier", "Санкт-Петербург", "regional", "Мобильная аналитика",
                TODAY.plusDays(20), "Host", "https://example.com", List.of("mobile", "analytics"),
                "Конференция о мобильных продуктах", "доклад", "русский", "middle", "hybrid", 40,
                "Практический опыт в мобильной разработке", null);

        Event event = single(full);

        assertThat(event.whyFits())
                .anyMatch(reason -> reason.contains("Язык мероприятия совпадает"))
                .anyMatch(reason -> reason.contains("Гибридный формат"))
                .anyMatch(reason -> reason.contains("Требование «Практический опыт в мобильной разработке» подтверждено"));
        assertThat(event.unknowns()).noneMatch(item -> item.contains("Язык мероприятия в данных CFP не указан"));
        assertThat(event.requirements()).contains("Выступление: доклад, 40 минут.");
        assertThat(event.cfp().daysLeft()).isEqualTo(20);
    }

    @Test
    void sparseCfpListsUnknownsInsteadOfGuessing() {
        CfpFact sparse = new CfpFact("c2", "Product Day", "Москва", "regional", "Продуктовые метрики",
                TODAY.plusDays(10), "Host", "https://example.com", List.of("product"));

        Event event = single(sparse);

        assertThat(event.unknowns())
                .contains("Язык мероприятия в данных CFP не указан.", "Формат и длительность выступления не указаны.",
                        "Требования к спикеру и к заявке в источнике не указаны.");
        assertThat(event.confidence().reasons()).anyMatch(reason -> reason.contains("мало сведений"));
    }

    @Test
    void languageMismatchIsNeverAConfidentRecommendation() {
        CfpFact english = new CfpFact("c3", "Mobile World", "Москва", "regional", "Мобильная аналитика",
                TODAY.plusDays(15), "Host", "https://example.com", List.of("mobile", "analytics"),
                null, "доклад", "английский", null, "offline", 30, null, null);

        CfpMatcher.Outcome outcome = CfpMatcher.match(anna, profile(anna, List.of()), List.of(english), TODAY, 8);

        assertThat(outcome.groups()).noneMatch(group -> group.code().equals(Recommendation.GROUP_HIGH));
        assertThat(outcome.groups().getFirst().items().getFirst().limitations())
                .anyMatch(reason -> reason.contains("на языке «английский»"));
    }

    @Test
    void skipsPastDeadlinesAndUnrelatedTopics() {
        CfpFact past = new CfpFact("c4", "Old Mobile", "Москва", "regional", "Мобильная аналитика", TODAY.minusDays(1),
                "Host", "https://example.com", List.of("mobile"));
        CfpFact backend = new CfpFact("c5", "Backend Conf", "Москва", "regional", "Распределённые системы", TODAY.plusDays(5),
                "Host", "https://example.com", List.of("backend"));

        CfpMatcher.Outcome outcome = CfpMatcher.match(anna, profile(anna, List.of()), List.of(past, backend), TODAY, 8);

        assertThat(outcome.groups()).isEmpty();
        assertThat(outcome.considered()).isEqualTo(1);
    }

    private Event single(CfpFact cfp) {
        CfpMatcher.Outcome outcome = CfpMatcher.match(anna, profile(anna, List.of()), List.of(cfp), TODAY, 8);
        return outcome.groups().getFirst().items().getFirst();
    }
}
