package ru.shaxowskiy.javaspeakerclub.producer.validation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerProperties;
import ru.shaxowskiy.javaspeakerclub.producer.model.PlanValidation;
import ru.shaxowskiy.javaspeakerclub.producer.model.PlanValidation.Rejection;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlan;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlan.EvidenceRef;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlan.Quote;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanDraft;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot.CfpItem;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot.FeedbackItem;
import ru.shaxowskiy.javaspeakerclub.producer.model.SpeakerSnapshot.TalkItem;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Пропускает в план только то, что подтверждено снимком: темы — отзывами, шаги — доказательствами.
 * Числа, цитаты и подписи доказательств собирает сам, а не берёт у LLM.
 */
@Component
@RequiredArgsConstructor
public class ProducerPlanValidator {

    private static final DateTimeFormatter RU_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final int MAX_QUOTES = 2;
    private static final int MAX_QUOTE_LENGTH = 220;
    private static final int MAX_LABEL_TEXT_LENGTH = 120;
    private static final int MAX_SUMMARY_LENGTH = 700;

    private final ProducerProperties properties;

    public record Result(ProducerPlan plan, PlanValidation validation) {
    }

    private enum ThemeKind {
        STRENGTH("Сильная сторона"),
        GROWTH("Зона роста");

        private final String label;

        ThemeKind(String label) {
            this.label = label;
        }
    }

    public Result validate(ProducerPlanDraft draft, SpeakerSnapshot snapshot, LocalDate today) {
        Map<String, FeedbackItem> feedback = index(snapshot.feedback(), FeedbackItem::id);
        Map<String, TalkItem> talks = index(snapshot.talks(), TalkItem::id);
        Map<String, CfpItem> cfps = index(snapshot.cfps(), CfpItem::id);
        List<Rejection> rejections = new ArrayList<>();

        List<ProducerPlan.Theme> strengths = validateThemes(draft.strengths(), ThemeKind.STRENGTH, feedback, rejections);
        List<ProducerPlan.Theme> growthAreas = validateThemes(draft.growthAreas(), ThemeKind.GROWTH, feedback, rejections);
        List<ProducerPlan.Step> steps = validateSteps(draft.steps(), feedback, talks, cfps, today, rejections);

        ProducerPlan plan = new ProducerPlan(
                summary(draft.summary()),
                toStage(snapshot),
                strengths,
                growthAreas,
                steps
        );
        PlanValidation validation = new PlanValidation(
                strengths.size() + growthAreas.size(),
                steps.size(),
                List.copyOf(rejections)
        );
        return new Result(plan, validation);
    }

    private List<ProducerPlan.Theme> validateThemes(
            List<ProducerPlanDraft.Theme> drafts,
            ThemeKind kind,
            Map<String, FeedbackItem> feedback,
            List<Rejection> rejections
    ) {
        List<ProducerPlan.Theme> accepted = new ArrayList<>();
        for (ProducerPlanDraft.Theme draft : nullSafe(drafts)) {
            String title = trimToNull(draft == null ? null : draft.title());
            if (title == null) {
                rejections.add(new Rejection(kind.label, "Тема без названия"));
                continue;
            }
            String item = kind.label + " «" + title + "»";

            LinkedHashSet<String> ids = new LinkedHashSet<>(nullSafe(draft.feedbackIds()));
            List<String> valid = ids.stream().filter(feedback::containsKey).toList();
            long unknown = ids.size() - valid.size();
            if (unknown > 0) {
                rejections.add(new Rejection(item, "Отброшены ссылки на отзывы, которых нет в данных спикера: " + unknown));
            }
            if (valid.size() < properties.minThemeMentions()) {
                rejections.add(new Rejection(item, "Подтверждена отзывами: " + valid.size()
                        + ", нужно минимум " + properties.minThemeMentions()));
                continue;
            }
            if (accepted.size() >= properties.maxThemesPerKind()) {
                rejections.add(new Rejection(item, "Сверх лимита в " + properties.maxThemesPerKind() + " темы"));
                continue;
            }

            List<Quote> quotes = valid.stream()
                    .map(id -> new Quote(id, truncate(themeText(feedback.get(id), kind), MAX_QUOTE_LENGTH)))
                    .filter(quote -> quote.text() != null)
                    .limit(MAX_QUOTES)
                    .toList();
            accepted.add(new ProducerPlan.Theme(title, valid.size(), valid, quotes));
        }
        return accepted;
    }

    private List<ProducerPlan.Step> validateSteps(
            List<ProducerPlanDraft.Step> drafts,
            Map<String, FeedbackItem> feedback,
            Map<String, TalkItem> talks,
            Map<String, CfpItem> cfps,
            LocalDate today,
            List<Rejection> rejections
    ) {
        List<ProducerPlan.Step> accepted = new ArrayList<>();
        for (ProducerPlanDraft.Step draft : nullSafe(drafts)) {
            String title = trimToNull(draft == null ? null : draft.title());
            if (title == null) {
                rejections.add(new Rejection("Шаг", "Шаг без названия"));
                continue;
            }
            String item = "Шаг «" + title + "»";

            List<EvidenceRef> evidence = new ArrayList<>();
            LocalDate earliestCfpDeadline = null;
            for (ProducerPlanDraft.Evidence ref : nullSafe(draft.evidence())) {
                String type = ref == null || ref.type() == null ? "" : ref.type().trim().toUpperCase(Locale.ROOT);
                String id = ref == null ? null : trimToNull(ref.id());
                EvidenceRef resolved = switch (type) {
                    case "FEEDBACK" -> feedback.containsKey(id)
                            ? new EvidenceRef(type, id, feedbackLabel(feedback.get(id)))
                            : null;
                    case "TALK" -> talks.containsKey(id)
                            ? new EvidenceRef(type, id, talkLabel(talks.get(id)))
                            : null;
                    case "CFP" -> cfps.containsKey(id) && !cfps.get(id).deadline().isBefore(today)
                            ? new EvidenceRef(type, id, cfpLabel(cfps.get(id)))
                            : null;
                    default -> null;
                };
                if (resolved == null) {
                    rejections.add(new Rejection(item, "Отброшено доказательство, которого нет в данных: " + type + " " + id));
                    continue;
                }
                if (evidence.stream().anyMatch(existing -> existing.id().equals(resolved.id()))) {
                    continue;
                }
                evidence.add(resolved);
                if ("CFP".equals(type)) {
                    LocalDate deadline = cfps.get(id).deadline();
                    if (earliestCfpDeadline == null || deadline.isBefore(earliestCfpDeadline)) {
                        earliestCfpDeadline = deadline;
                    }
                }
            }

            if (evidence.isEmpty()) {
                rejections.add(new Rejection(item, "Нет ни одного подтверждённого доказательства"));
                continue;
            }
            if (accepted.size() >= properties.maxSteps()) {
                rejections.add(new Rejection(item, "Сверх лимита в " + properties.maxSteps() + " шага"));
                continue;
            }

            LocalDate dueDate = parseDate(draft.dueDate());
            if (dueDate != null && dueDate.isBefore(today)) {
                rejections.add(new Rejection(item, "Срок " + dueDate + " в прошлом, срок снят"));
                dueDate = null;
            }
            if (earliestCfpDeadline != null && (dueDate == null || dueDate.isAfter(earliestCfpDeadline))) {
                if (dueDate != null) {
                    rejections.add(new Rejection(item, "Срок сдвинут до дедлайна CFP " + earliestCfpDeadline));
                }
                dueDate = earliestCfpDeadline;
            }

            accepted.add(new ProducerPlan.Step(title, trimToNull(draft.why()), dueDate, List.copyOf(evidence)));
        }
        return accepted;
    }

    private static ProducerPlan.Stage toStage(SpeakerSnapshot snapshot) {
        return new ProducerPlan.Stage(
                snapshot.speaker().stage(),
                snapshot.speaker().nextStage(),
                snapshot.activity().momentum(),
                snapshot.nps().current(),
                snapshot.nps().delta(),
                snapshot.activity().daysSinceLastTalk()
        );
    }

    private static String summary(String raw) {
        String summary = trimToNull(raw);
        return summary == null ? "План собран по данным спикера." : truncate(summary, MAX_SUMMARY_LENGTH);
    }

    private static String themeText(FeedbackItem item, ThemeKind kind) {
        if (kind == ThemeKind.STRENGTH) {
            return firstMeaningful(item.positive(), item.improvement());
        }
        return firstMeaningful(item.negative(), item.improvement(), item.positive());
    }

    private static String feedbackLabel(FeedbackItem item) {
        String text = firstMeaningful(item.negative(), item.improvement(), item.positive());
        String base = "Отзыв " + item.rating() + "/10";
        return text == null ? base : base + ": «" + truncate(text, MAX_LABEL_TEXT_LENGTH) + "»";
    }

    private static String talkLabel(TalkItem talk) {
        String date = talk.date() == null ? "" : ", " + talk.date().format(RU_DATE);
        return "Доклад «" + talk.topic() + "»" + date;
    }

    private static String cfpLabel(CfpItem cfp) {
        return "CFP «" + cfp.title() + "», дедлайн " + cfp.deadline().format(RU_DATE);
    }

    private static String firstMeaningful(String... texts) {
        for (String text : texts) {
            if (isMeaningful(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private static boolean isMeaningful(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.length() < 3 || trimmed.matches("[-—–.\\s]+")) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return !lower.equals("нет") && !lower.equals("всё отлично") && !lower.equals("замечаний нет");
    }

    private static LocalDate parseDate(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.equalsIgnoreCase("null")) {
            return null;
        }
        try {
            return LocalDate.parse(trimmed.length() > 10 ? trimmed.substring(0, 10) : trimmed);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max - 1).trim() + "…";
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    private static <T> Map<String, T> index(List<T> items, Function<T, String> id) {
        return nullSafe(items).stream().collect(Collectors.toMap(id, Function.identity(), (left, right) -> left));
    }
}
