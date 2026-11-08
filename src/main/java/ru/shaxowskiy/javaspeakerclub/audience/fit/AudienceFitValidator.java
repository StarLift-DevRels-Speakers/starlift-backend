package ru.shaxowskiy.javaspeakerclub.audience.fit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.audience.Rejection;
import ru.shaxowskiy.javaspeakerclub.audience.Texts;
import ru.shaxowskiy.javaspeakerclub.audience.material.Fragment;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialSnapshot;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialSnapshot.VisualStats;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Пропускает в итог только то, что опирается на материал: замечания и сильные стороны должны ссылаться на
 * существующие фрагменты. Итоговую оценку и вердикт считает сам по оценкам критериев.
 */
@Component
@RequiredArgsConstructor
public class AudienceFitValidator {

    public static final String FITS = "fits";
    public static final String PARTIALLY = "partially";
    public static final String NOT_FITS = "not_fits";

    private static final Map<String, CriterionSpec> CRITERIA = criteria(
            new CriterionSpec("level", "Уровень сложности", 0.25),
            new CriterionSpec("relevance", "Тема и роли слушателей", 0.20),
            new CriterionSpec("expectations", "Ожидания аудитории", 0.15),
            new CriterionSpec("knowledge", "Требуемые знания", 0.15),
            new CriterionSpec("presentation", "Подача и наглядность", 0.15),
            new CriterionSpec("turnoffs", "Нет того, что отталкивает", 0.10)
    );
    private static final int MAX_ISSUES = 6;
    private static final int MAX_STRENGTHS = 4;
    private static final int MAX_QUOTE_LENGTH = 220;
    private static final int MAX_TEXT_LENGTH = 400;
    private static final int MAX_SUMMARY_LENGTH = 700;
    private static final String PRESENTATION = "presentation";
    /** Аудитории, которой важна наглядность, не подходит презентация, где без картинок больше половины слайдов. */
    private static final double TEXT_ONLY_LIMIT = 0.5;
    private static final int MIN_SLIDES_FOR_VISUAL_RULES = 3;
    private static final int TEXT_ONLY_PRESENTATION_CAP = 4;
    private static final int MAX_LISTED_SLIDES = 6;

    private final AudienceProperties properties;

    record CriterionSpec(String key, String label, double weight) {
    }

    public record Result(AudienceFitResult result, List<Rejection> rejections) {
    }

    /**
     * @throws IllegalStateException если модель не оценила ни одного известного критерия — оценку не из чего считать
     */
    public Result validate(AudienceFitDraft draft, MaterialSnapshot material, EventAudienceProfile profile) {
        Map<String, Fragment> fragments = material.fragments().stream()
                .collect(Collectors.toMap(Fragment::id, Function.identity(), (left, right) -> left));
        List<Rejection> rejections = new ArrayList<>();
        VisualStats visual = material.visual();
        String visualExpectation = profile.visualExpectation() == null ? "medium" : profile.visualExpectation();

        List<AudienceFitResult.Criterion> criteria = presentation(validateCriteria(draft.criteria(), rejections),
                visual, visualExpectation, rejections);
        if (criteria.isEmpty()) {
            throw new IllegalStateException("Модель не оценила ни одного критерия");
        }
        int score = score(criteria);
        String verdict = verdict(score);
        String modelVerdict = draft.verdict() == null ? null : draft.verdict().trim().toLowerCase(Locale.ROOT);
        if (modelVerdict != null && !modelVerdict.equals(verdict)) {
            rejections.add(new Rejection("Вердикт", "Вердикт модели «" + modelVerdict + "» заменён на «" + verdict
                    + "» по итоговой оценке " + score));
        }

        List<AudienceFitResult.Issue> issues = new ArrayList<>(visualIssues(visual, visualExpectation, fragments, profile.segmentLabel()));
        for (AudienceFitResult.Issue issue : validateIssues(draft.issues(), fragments, rejections)) {
            if (issues.size() < MAX_ISSUES + 2) {
                issues.add(issue);
            }
        }

        AudienceFitResult result = new AudienceFitResult(
                verdict,
                score,
                Texts.truncate(Texts.trimToNull(draft.summary()), MAX_SUMMARY_LENGTH),
                criteria,
                List.copyOf(issues),
                validateStrengths(draft.strengths(), fragments, rejections),
                profile.confidence(),
                material.truncated(),
                profile.primarySegment(),
                profile.segmentLabel(),
                visual
        );
        return new Result(result, List.copyOf(rejections));
    }

    /**
     * Подачу без слайдов не оценить — критерий снимается. Если модель его не дала, сервер ставит оценку по доле
     * слайдов с картинками. Аудитории, которой важна наглядность, презентация из сплошного текста не подходит:
     * оценка ограничивается сверху.
     */
    static List<AudienceFitResult.Criterion> presentation(
            List<AudienceFitResult.Criterion> criteria,
            VisualStats visual,
            String visualExpectation,
            List<Rejection> rejections
    ) {
        List<AudienceFitResult.Criterion> result = new ArrayList<>(criteria);
        AudienceFitResult.Criterion current = result.stream().filter(item -> PRESENTATION.equals(item.key())).findFirst().orElse(null);
        String label = CRITERIA.get(PRESENTATION).label();
        if (visual == null) {
            if (current != null) {
                result.remove(current);
                rejections.add(new Rejection(label, "Снят: у материала нет слайдов"));
            }
            return List.copyOf(result);
        }
        if (current == null) {
            int computed = (int) Math.round(10.0 * visual.slidesWithImages() / visual.slides());
            current = new AudienceFitResult.Criterion(PRESENTATION, label, computed,
                    "Слайдов с картинками или диаграммами: " + visual.slidesWithImages() + " из " + visual.slides());
            result.add(current);
            rejections.add(new Rejection(label, "Модель не оценила — посчитано по доле слайдов с картинками"));
        }
        boolean textHeavy = visual.slides() >= MIN_SLIDES_FOR_VISUAL_RULES && visual.textOnlyShare() > TEXT_ONLY_LIMIT;
        if ("high".equals(visualExpectation) && textHeavy && current.score() > TEXT_ONLY_PRESENTATION_CAP) {
            result.set(result.indexOf(current), new AudienceFitResult.Criterion(PRESENTATION, label, TEXT_ONLY_PRESENTATION_CAP,
                    current.comment()));
            rejections.add(new Rejection(label, "Оценка " + current.score() + " снижена до " + TEXT_ONLY_PRESENTATION_CAP
                    + ": аудитории важна наглядность, а без картинок " + visual.textOnlySlides().size() + " из " + visual.slides() + " слайдов"));
        }
        List<String> order = List.copyOf(CRITERIA.keySet());
        result.sort((left, right) -> Integer.compare(order.indexOf(left.key()), order.indexOf(right.key())));
        return List.copyOf(result);
    }

    /**
     * Замечания по наглядности считает сервер: слайды без иллюстраций для аудитории, которой нужна наглядность,
     * и «стены текста» для всех, кроме аудитории, которой наглядность не важна.
     */
    static List<AudienceFitResult.Issue> visualIssues(VisualStats visual, String visualExpectation,
                                                      Map<String, Fragment> fragments, String segmentLabel) {
        if (visual == null || visual.slides() < MIN_SLIDES_FOR_VISUAL_RULES) {
            return List.of();
        }
        String audience = segmentLabel == null ? "этой аудитории" : "аудитории «" + segmentLabel + "»";
        List<AudienceFitResult.Issue> issues = new ArrayList<>();
        if ("high".equals(visualExpectation) && visual.textOnlyShare() > TEXT_ONLY_LIMIT) {
            issues.add(slidesIssue(visual.textOnlySlides(), fragments,
                    "Без картинок и схем " + visual.textOnlySlides().size() + " из " + visual.slides() + " слайдов, а "
                            + audience + " важна наглядность",
                    "Добавь схемы, скриншоты или иллюстрации хотя бы на ключевые слайды"));
        }
        if (!"low".equals(visualExpectation) && !visual.wallOfTextSlides().isEmpty()) {
            issues.add(slidesIssue(visual.wallOfTextSlides(), fragments,
                    "Слишком много текста на слайде — из зала его не прочитать",
                    "Оставь на слайде тезисы, а подробности перенеси в речь или заметки"));
        }
        return issues;
    }

    private static AudienceFitResult.Issue slidesIssue(List<String> labels, Map<String, Fragment> fragments, String problem, String fix) {
        String firstLabel = labels.getFirst();
        Fragment first = fragments.values().stream().filter(fragment -> fragment.label().equals(firstLabel)).findFirst().orElse(null);
        String listed = String.join(", ", labels.subList(0, Math.min(MAX_LISTED_SLIDES, labels.size())))
                + (labels.size() > MAX_LISTED_SLIDES ? " и ещё " + (labels.size() - MAX_LISTED_SLIDES) : "");
        return new AudienceFitResult.Issue(
                first == null ? "visual" : first.id(),
                listed,
                first == null ? null : Texts.truncate(first.text(), MAX_QUOTE_LENGTH),
                problem,
                fix
        );
    }

    String verdict(int score) {
        if (score >= properties.fitThreshold()) {
            return FITS;
        }
        return score >= properties.partialThreshold() ? PARTIALLY : NOT_FITS;
    }

    /**
     * Взвешенное среднее оценок 0–10, переведённое в 0–100; веса неоценённых критериев не учитываются.
     */
    static int score(List<AudienceFitResult.Criterion> criteria) {
        double weighted = 0;
        double weights = 0;
        for (AudienceFitResult.Criterion criterion : criteria) {
            double weight = CRITERIA.get(criterion.key()).weight();
            weighted += criterion.score() * weight;
            weights += weight;
        }
        return (int) Math.round(weighted / weights * 10);
    }

    private static List<AudienceFitResult.Criterion> validateCriteria(List<AudienceFitDraft.Criterion> drafts, List<Rejection> rejections) {
        Map<String, AudienceFitResult.Criterion> accepted = new LinkedHashMap<>();
        for (AudienceFitDraft.Criterion draft : nonNull(drafts)) {
            String key = draft.key() == null ? "" : draft.key().trim().toLowerCase(Locale.ROOT);
            CriterionSpec spec = CRITERIA.get(key);
            if (spec == null) {
                rejections.add(new Rejection("Критерий «" + draft.key() + "»", "Неизвестный критерий"));
                continue;
            }
            Double raw = Texts.number(draft.score());
            if (raw == null) {
                rejections.add(new Rejection(spec.label(), "Нет оценки"));
                continue;
            }
            int score = (int) Math.round(Math.max(0, Math.min(10, raw)));
            if (score != raw) {
                rejections.add(new Rejection(spec.label(), "Оценка " + raw + " приведена к " + score));
            }
            accepted.putIfAbsent(key, new AudienceFitResult.Criterion(key, spec.label(), score,
                    Texts.truncate(Texts.trimToNull(draft.comment()), MAX_TEXT_LENGTH)));
        }
        // Порядок критериев — как в спецификации, а не как ответила модель.
        return CRITERIA.keySet().stream().filter(accepted::containsKey).map(accepted::get).toList();
    }

    private static List<AudienceFitResult.Issue> validateIssues(
            List<AudienceFitDraft.Issue> drafts,
            Map<String, Fragment> fragments,
            List<Rejection> rejections
    ) {
        List<AudienceFitResult.Issue> accepted = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (AudienceFitDraft.Issue draft : nonNull(drafts)) {
            String problem = Texts.trimToNull(draft.problem());
            if (problem == null) {
                continue;
            }
            Fragment fragment = fragment(draft.fragmentId(), fragments);
            if (fragment == null) {
                rejections.add(new Rejection("Замечание «" + Texts.truncate(problem, 60) + "»",
                        "Ссылка на фрагмент, которого нет в материале: " + draft.fragmentId()));
                continue;
            }
            if (!seen.add(fragment.id() + "\u0000" + problem) || accepted.size() >= MAX_ISSUES) {
                continue;
            }
            accepted.add(new AudienceFitResult.Issue(
                    fragment.id(),
                    fragment.label(),
                    Texts.truncate(fragment.text(), MAX_QUOTE_LENGTH),
                    Texts.truncate(problem, MAX_TEXT_LENGTH),
                    Texts.truncate(Texts.trimToNull(draft.fix()), MAX_TEXT_LENGTH)
            ));
        }
        return List.copyOf(accepted);
    }

    private static List<AudienceFitResult.Strength> validateStrengths(
            List<AudienceFitDraft.Strength> drafts,
            Map<String, Fragment> fragments,
            List<Rejection> rejections
    ) {
        List<AudienceFitResult.Strength> accepted = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (AudienceFitDraft.Strength draft : nonNull(drafts)) {
            String why = Texts.trimToNull(draft.why());
            if (why == null) {
                continue;
            }
            Fragment fragment = fragment(draft.fragmentId(), fragments);
            if (fragment == null) {
                rejections.add(new Rejection("Сильная сторона «" + Texts.truncate(why, 60) + "»",
                        "Ссылка на фрагмент, которого нет в материале: " + draft.fragmentId()));
                continue;
            }
            if (!seen.add(fragment.id()) || accepted.size() >= MAX_STRENGTHS) {
                continue;
            }
            accepted.add(new AudienceFitResult.Strength(
                    fragment.id(),
                    fragment.label(),
                    Texts.truncate(fragment.text(), MAX_QUOTE_LENGTH),
                    Texts.truncate(why, MAX_TEXT_LENGTH)
            ));
        }
        return List.copyOf(accepted);
    }

    private static Fragment fragment(String id, Map<String, Fragment> fragments) {
        String trimmed = Texts.trimToNull(id);
        return trimmed == null ? null : fragments.get(trimmed.toLowerCase(Locale.ROOT));
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list.stream().filter(item -> item != null).toList();
    }

    private static Map<String, CriterionSpec> criteria(CriterionSpec... specs) {
        Map<String, CriterionSpec> map = new LinkedHashMap<>();
        for (CriterionSpec spec : specs) {
            map.put(spec.key(), spec);
        }
        return map;
    }
}
