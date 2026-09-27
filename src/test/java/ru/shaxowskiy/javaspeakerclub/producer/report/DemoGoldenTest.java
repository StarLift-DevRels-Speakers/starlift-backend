package ru.shaxowskiy.javaspeakerclub.producer.report;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlan;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DemoGoldenTest {

    private final DemoGolden golden = new DemoGolden(Map.of("anna", List.of(
            new DemoGolden.Pattern("strength", "Живые кейсы", List.of("s1", "s2", "s3"), false),
            new DemoGolden.Pattern("growth", "Нет времени на Q&A", List.of("g1", "g2", "g3"), false),
            new DemoGolden.Pattern("growth", "Непонятно, как применить", List.of("l1", "l2"), true)
    )));

    @Test
    void matchesThemesByCitedFeedbackAndFlagsUnplannedOnes() {
        ProducerPlan plan = plan(
                List.of(theme("Реальные примеры", "s1", "s2", "x1")),
                List.of(theme("Короткий Q&A", "g1", "g3", "g4"), theme("Мелкий шрифт", "x2", "x3", "g2"))
        );

        DemoGolden.Check check = golden.check("anna", plan);

        assertThat(check.expected()).isEqualTo(2);
        assertThat(check.found()).isEqualTo(2);
        assertThat(check.patterns()).filteredOn(DemoGolden.PatternCheck::liveDemo)
                .singleElement()
                .satisfies(live -> assertThat(live.found()).isFalse());
        assertThat(check.unplannedThemes()).containsExactly("Зона роста «Мелкий шрифт»");
    }

    @Test
    void doesNotCountSingleOverlapAsFound() {
        ProducerPlan plan = plan(List.of(theme("Кейсы", "s1", "x1", "x2")), List.of());

        DemoGolden.Check check = golden.check("anna", plan);

        assertThat(check.found()).isZero();
        assertThat(golden.covers("anna")).isTrue();
        assertThat(golden.covers("bob")).isFalse();
    }

    private static ProducerPlan plan(List<ProducerPlan.Theme> strengths, List<ProducerPlan.Theme> growth) {
        return new ProducerPlan("…", null, strengths, growth, List.of());
    }

    private static ProducerPlan.Theme theme(String title, String... ids) {
        return new ProducerPlan.Theme(title, ids.length, List.of(ids), List.of());
    }
}
