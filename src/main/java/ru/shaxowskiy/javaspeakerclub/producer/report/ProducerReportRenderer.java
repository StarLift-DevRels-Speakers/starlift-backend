package ru.shaxowskiy.javaspeakerclub.producer.report;

import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;
import ru.shaxowskiy.javaspeakerclub.producer.model.PlanValidation;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlan;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * HTML-отчёт прогона продюсера: сводка по демо-спикерам, планы с доказательствами,
 * сверка с эталоном и всё, что отбросил валидатор.
 */
@Component
public class ProducerReportRenderer {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private static final Map<String, String> STAGES = Map.of(
            "local", "локальный",
            "regional", "региональный",
            "federal", "федеральный",
            "international", "международный"
    );
    private static final Map<String, String> MOMENTUM = Map.of(
            "growing", "растёт",
            "stable", "стабильно",
            "declining", "спад",
            "inactive", "не выступал полгода"
    );
    private static final Map<String, String> STATUSES = Map.of(
            "ready", "готов",
            "insufficient_data", "мало данных",
            "failed", "ошибка"
    );

    public record Row(
            Long speakerId,
            String username,
            String name,
            String status,
            LocalDateTime createdDate,
            String model,
            String promptVersion,
            String error,
            SpeakerSnapshot snapshot,
            ProducerPlan plan,
            PlanValidation validation,
            DemoGolden.Check golden
    ) {
    }

    public String render(List<Row> rows, LocalDateTime generatedAt) {
        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="ru">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Отчёт продюсера</title>
                <style>
                  body { font-family: -apple-system, "Segoe UI", Roboto, sans-serif; margin: 0; background: #f6f7f9; color: #1f2328; line-height: 1.5; }
                  main { max-width: 1040px; margin: 0 auto; padding: 24px 16px 48px; }
                  h1 { font-size: 24px; margin: 0 0 4px; } h2 { font-size: 20px; margin: 0; } h3 { font-size: 15px; margin: 18px 0 6px; }
                  .muted { color: #656d76; font-size: 13px; }
                  table { width: 100%; border-collapse: collapse; background: #fff; border: 1px solid #d0d7de; border-radius: 8px; overflow: hidden; margin: 16px 0 24px; font-size: 14px; }
                  th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #eaeef2; vertical-align: top; }
                  th { background: #f6f8fa; font-weight: 600; }
                  .card { background: #fff; border: 1px solid #d0d7de; border-radius: 8px; padding: 16px 20px; margin: 16px 0; }
                  .head { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; align-items: baseline; }
                  .badge { display: inline-block; font-size: 12px; padding: 2px 8px; border-radius: 10px; margin-right: 4px; }
                  .ok { background: #dafbe1; color: #116329; } .warn { background: #fff8c5; color: #7d4e00; }
                  .bad { background: #ffebe9; color: #a40e26; } .neutral { background: #eaeef2; color: #424a53; }
                  .facts { display: flex; gap: 16px; flex-wrap: wrap; font-size: 13px; color: #424a53; margin: 8px 0; }
                  .summary { background: #f6f8fa; border-radius: 6px; padding: 10px 12px; margin: 10px 0; }
                  ul { margin: 4px 0; padding-left: 20px; } li { margin: 4px 0; }
                  .quote { color: #424a53; font-size: 13px; }
                  .evidence { font-size: 13px; color: #424a53; }
                  code { font-size: 12px; background: #eaeef2; padding: 1px 4px; border-radius: 4px; }
                </style>
                </head>
                <body><main>
                """);
        html.append("<h1>Отчёт продюсера</h1>");
        html.append("<div class=\"muted\">Сформирован ").append(generatedAt.format(DATE_TIME))
                .append(" · спикеров: ").append(rows.size()).append("</div>");

        appendSummaryTable(html, rows);
        rows.forEach(row -> appendSpeaker(html, row));

        html.append("</main></body></html>\n");
        return html.toString();
    }

    private void appendSummaryTable(StringBuilder html, List<Row> rows) {
        html.append("<table><thead><tr><th>Спикер</th><th>Статус</th><th>Заложенные паттерны</th>"
                + "<th>Лишние темы</th><th>Отброшено</th><th>План от</th></tr></thead><tbody>");
        for (Row row : rows) {
            html.append("<tr><td>").append(esc(row.name())).append("<br><span class=\"muted\">")
                    .append(esc(row.username())).append("</span></td>");
            html.append("<td>").append(statusBadge(row.status())).append("</td>");
            if (row.golden() == null) {
                html.append("<td class=\"muted\">нет эталона</td><td class=\"muted\">—</td>");
            } else {
                String css = row.golden().found() == row.golden().expected() ? "ok" : "warn";
                html.append("<td><span class=\"badge ").append(css).append("\">")
                        .append(row.golden().found()).append(" из ").append(row.golden().expected())
                        .append("</span></td>");
                html.append("<td>").append(row.golden().unplannedThemes().size()).append("</td>");
            }
            int rejected = row.validation() == null ? 0 : row.validation().rejections().size();
            html.append("<td>").append(rejected).append("</td>");
            html.append("<td>").append(row.createdDate() == null ? "—" : row.createdDate().format(DATE_TIME))
                    .append("</td></tr>");
        }
        html.append("</tbody></table>");
    }

    private void appendSpeaker(StringBuilder html, Row row) {
        html.append("<section class=\"card\"><div class=\"head\"><h2>").append(esc(row.name()))
                .append(" <span class=\"muted\">").append(esc(row.username())).append("</span></h2>")
                .append(statusBadge(row.status())).append("</div>");

        if (row.snapshot() != null) {
            appendFacts(html, row.snapshot());
        }
        if (row.error() != null) {
            html.append("<p><span class=\"badge bad\">ошибка</span> <code>").append(esc(row.error())).append("</code></p>");
        }
        if (row.plan() != null) {
            appendPlan(html, row.plan());
        } else if (row.status() == null) {
            html.append("<p class=\"muted\">План ещё не сформирован.</p>");
        }
        if (row.golden() != null) {
            appendGolden(html, row.golden());
        }
        if (row.validation() != null && !row.validation().rejections().isEmpty()) {
            html.append("<h3>Что отбросил валидатор</h3><ul>");
            for (PlanValidation.Rejection rejection : row.validation().rejections()) {
                html.append("<li>").append(esc(rejection.item())).append(": <span class=\"muted\">")
                        .append(esc(rejection.reason())).append("</span></li>");
            }
            html.append("</ul>");
        }
        html.append("<p class=\"muted\">Модель: ").append(esc(orDash(row.model())))
                .append(" · промпт: ").append(esc(orDash(row.promptVersion())))
                .append(row.createdDate() == null ? "" : " · " + row.createdDate().format(DATE_TIME))
                .append("</p></section>");
    }

    private void appendFacts(StringBuilder html, SpeakerSnapshot snapshot) {
        SpeakerSnapshot.Activity activity = snapshot.activity();
        SpeakerSnapshot.Nps nps = snapshot.nps();
        html.append("<div class=\"facts\">");
        html.append("<span>Стадия: ").append(esc(STAGES.getOrDefault(snapshot.speaker().stage(), snapshot.speaker().stage())));
        if (snapshot.speaker().nextStage() != null) {
            html.append(" → ").append(esc(STAGES.getOrDefault(snapshot.speaker().nextStage(), snapshot.speaker().nextStage())));
        }
        html.append("</span>");
        html.append("<span>Динамика: ").append(esc(MOMENTUM.getOrDefault(activity.momentum(), activity.momentum())))
                .append(" (").append(activity.talksLast90Days()).append(" за 90 дн. против ")
                .append(activity.talksPrevious90Days()).append(")</span>");
        if (nps.current() != null) {
            html.append("<span>NPS: ").append(nps.current());
            if (nps.delta() != null) {
                html.append(nps.delta() >= 0 ? " (+" : " (").append(nps.delta()).append(")");
            }
            html.append("</span>");
        }
        html.append("<span>Отзывов: ").append(snapshot.feedbackStats().count());
        if (snapshot.feedbackStats().averageRating() != null) {
            html.append(", средняя ").append(snapshot.feedbackStats().averageRating());
        }
        html.append("</span><span>Открытых CFP: ").append(snapshot.cfps().size()).append("</span></div>");
    }

    private void appendPlan(StringBuilder html, ProducerPlan plan) {
        html.append("<div class=\"summary\">").append(esc(plan.summary())).append("</div>");
        appendThemes(html, "Сильные стороны", plan.strengths());
        appendThemes(html, "Зоны роста", plan.growthAreas());

        html.append("<h3>План</h3>");
        if (plan.steps().isEmpty()) {
            html.append("<p class=\"muted\">Нет шагов с подтверждёнными доказательствами.</p>");
            return;
        }
        html.append("<ol>");
        for (ProducerPlan.Step step : plan.steps()) {
            html.append("<li><strong>").append(esc(step.title())).append("</strong>");
            if (step.dueDate() != null) {
                html.append(" <span class=\"badge neutral\">до ").append(step.dueDate().format(DATE)).append("</span>");
            }
            if (step.why() != null) {
                html.append("<br>").append(esc(step.why()));
            }
            html.append("<ul>");
            for (ProducerPlan.EvidenceRef ref : step.evidence()) {
                html.append("<li class=\"evidence\">").append(esc(ref.label())).append(" <code>")
                        .append(esc(ref.id())).append("</code></li>");
            }
            html.append("</ul></li>");
        }
        html.append("</ol>");
    }

    private void appendThemes(StringBuilder html, String title, List<ProducerPlan.Theme> themes) {
        html.append("<h3>").append(title).append("</h3>");
        if (themes.isEmpty()) {
            html.append("<p class=\"muted\">Нет тем, подтверждённых отзывами.</p>");
            return;
        }
        html.append("<ul>");
        for (ProducerPlan.Theme theme : themes) {
            html.append("<li><strong>").append(esc(theme.title())).append("</strong> <span class=\"muted\">· ")
                    .append(theme.mentions()).append(" ").append(reviews(theme.mentions())).append("</span>");
            for (ProducerPlan.Quote quote : theme.quotes()) {
                html.append("<div class=\"quote\">«").append(esc(quote.text())).append("»</div>");
            }
            html.append("</li>");
        }
        html.append("</ul>");
    }

    private void appendGolden(StringBuilder html, DemoGolden.Check golden) {
        html.append("<h3>Сверка с эталоном</h3><ul>");
        for (DemoGolden.PatternCheck check : golden.patterns()) {
            String kind = "strength".equals(check.kind()) ? "сильная сторона" : "зона роста";
            html.append("<li>");
            if (check.found()) {
                html.append("<span class=\"badge ok\">найдено</span>");
            } else if (check.liveDemo()) {
                html.append("<span class=\"badge neutral\">появится после живого шага</span>");
            } else {
                html.append("<span class=\"badge bad\">не найдено</span>");
            }
            html.append(esc(check.pattern())).append(" <span class=\"muted\">(").append(kind);
            if (check.matchedTheme() != null) {
                html.append(" → «").append(esc(check.matchedTheme())).append("»");
            }
            html.append(")</span></li>");
        }
        for (String unplanned : golden.unplannedThemes()) {
            html.append("<li><span class=\"badge warn\">не заложено, проверить глазами</span>")
                    .append(esc(unplanned)).append("</li>");
        }
        html.append("</ul>");
    }

    private static String statusBadge(String status) {
        if (status == null) {
            return "<span class=\"badge neutral\">нет плана</span>";
        }
        String css = switch (status) {
            case "ready" -> "ok";
            case "insufficient_data" -> "warn";
            case "failed" -> "bad";
            default -> "neutral";
        };
        return "<span class=\"badge " + css + "\">" + esc(STATUSES.getOrDefault(status, status)) + "</span>";
    }

    private static String reviews(int count) {
        int mod100 = count % 100;
        int mod10 = count % 10;
        if (mod100 >= 11 && mod100 <= 14) {
            return "отзывов";
        }
        if (mod10 == 1) {
            return "отзыв";
        }
        if (mod10 >= 2 && mod10 <= 4) {
            return "отзыва";
        }
        return "отзывов";
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private static String esc(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
