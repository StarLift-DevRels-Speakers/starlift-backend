package ru.shaxowskiy.javaspeakerclub.producer.matching.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TermMatcherTest {

    @Test
    void oneSharedWordIsNotEnoughForMultiWordTopic() {
        TermMatcher.Term topic = TermMatcher.term("Инженерия данных");

        assertThat(TermMatcher.matches(topic, TermMatcher.keys("Как говорить с бизнесом на языке данных"))).isFalse();
        assertThat(TermMatcher.matches(topic, TermMatcher.keys("Инженерия данных в продакшене"))).isTrue();
    }

    @Test
    void russianTopicFindsEnglishTagsAndWholeTagCounts() {
        assertThat(TermMatcher.matches(TermMatcher.term("Продуктовая аналитика"), TermMatcher.keys("product analytics"))).isTrue();
        assertThat(TermMatcher.matches(TermMatcher.term("мобильная разработка"), TermMatcher.keys("mobile"))).isTrue();
        assertThat(TermMatcher.matches(TermMatcher.term("data-quality"), TermMatcher.keys("data-quality"))).isTrue();
        assertThat(TermMatcher.matches(TermMatcher.term("data-quality"), TermMatcher.keys("data"))).isFalse();
    }

    @Test
    void longTopicNeedsAtLeastHalfOfItsWords() {
        TermMatcher.Term topic = TermMatcher.term("продуктовая аналитика мобильных приложений");

        assertThat(TermMatcher.matches(topic, TermMatcher.keys("Аналитика для мобильных команд"))).isTrue();
        assertThat(TermMatcher.matches(topic, TermMatcher.keys("Flutter mobile"))).isFalse();
    }
}
