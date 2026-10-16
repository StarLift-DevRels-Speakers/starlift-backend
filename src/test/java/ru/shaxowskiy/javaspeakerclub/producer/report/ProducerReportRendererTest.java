package ru.shaxowskiy.javaspeakerclub.producer.report;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.producer.model.PlanValidation;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProducerReportRendererTest {

    private final ProducerReportRenderer renderer = new ProducerReportRenderer();

    @Test
    void escapesUserTextAndShowsGoldenSummary() {
        ProducerPlan plan = new ProducerPlan(
                "Сводка <b>жирно</b>",
                new ProducerPlan.Stage("federal", "international", "growing", 88, 6, 26),
                List.of(new ProducerPlan.Theme("Кейсы", 3, List.of("f1", "f2", "f3"),
                        List.of(new ProducerPlan.Quote("f1", "<script>alert(1)</script>")))),
                List.of(),
                List.of(new ProducerPlan.Step("Подать заявку", "Потому что", LocalDate.of(2026, 10, 20),
                        List.of(new ProducerPlan.EvidenceRef("CFP", "c1", "CFP «Mobile Frontier», дедлайн 20.10.2026"))))
        );
        DemoGolden.Check golden = new DemoGolden.Check(
                List.of(new DemoGolden.PatternCheck("strength", "Живые кейсы", false, true, "Кейсы")),
                List.of(),
                1,
                1
        );
        ProducerReportRenderer.Row row = new ProducerReportRenderer.Row(
                1L, "anna.levina", "Анна Левина", "ready", LocalDateTime.of(2026, 9, 27, 12, 0),
                "gpt-4o-mini", "producer-v1", null, null, plan,
                new PlanValidation(1, 1, List.of(new PlanValidation.Rejection("Шаг «X»", "Нет доказательств"))),
                golden
        );

        String html = renderer.render(List.of(row), LocalDateTime.of(2026, 9, 27, 12, 5));

        assertThat(html).doesNotContain("<script>alert(1)</script>").contains("&lt;script&gt;");
        assertThat(html).doesNotContain("<b>жирно</b>");
        assertThat(html).contains("1 из 1", "Живые кейсы", "Подать заявку", "до 20.10.2026", "Нет доказательств");
        assertThat(html).contains("3 отзыва");
    }

    @Test
    void rendersSpeakerWithoutPlan() {
        ProducerReportRenderer.Row row = new ProducerReportRenderer.Row(
                2L, "timur", "Тимур", null, null, null, null, null, null, null, null, null
        );

        String html = renderer.render(List.of(row), LocalDateTime.of(2026, 9, 27, 12, 5));

        assertThat(html).contains("нет плана", "План ещё не сформирован.");
    }
}
