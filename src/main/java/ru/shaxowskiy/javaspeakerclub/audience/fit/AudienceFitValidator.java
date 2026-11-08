package ru.shaxowskiy.javaspeakerclub.audience.fit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceProperties;
import ru.shaxowskiy.javaspeakerclub.audience.Rejection;
import ru.shaxowskiy.javaspeakerclub.audience.Texts;
import ru.shaxowskiy.javaspeakerclub.audience.material.Fragment;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialSnapshot;

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
            new CriterionSpec("level", "Уровень сложности", 0.30),
            new CriterionSpec("relevance", "Тема и роли слушателей", 0.25),
            new CriterionSpec("expectations", "Ожидания аудитории", 0.20),
            new CriterionSpec("knowledge", "Требуемые знания", 0.15),
            new CriterionSpec("turnoffs", "Нет того, что отталкивает", 0.10)
    );
    private static final int MAX_ISSUES = 6;
    private static final int MAX_STRENGTHS = 4;
    private static final int MAX_QUOTE_LENGTH = 220;
    private static final int MAX_TEXT_LENGTH = 400;
    private static final int MAX_SUMMARY_LENGTH = 700;

    private final AudienceProperties properties;

    record CriterionSpec(String key, String label, double weight) {
    }

    public record Result(AudienceFitResult result, List<Rejection> rejections) {
    }

    /**
     * @throws IllegalStateException если модель не оценила ни одного известного критерия — оценку не из чего считать
     */
    public Result validate(AudienceFitDraft draft, MaterialSnapshot material, String profileConfidence) {
        Map<String, Fragment> fragments = material.fragments().stream()
                .collect(Collectors.toMap(Fragment::id, Function.identity(), (left, right) -> left));
        List<Rejection> rejections = new ArrayList<>();

        List<AudienceFitResult.Criterion> criteria = validateCriteria(draft.criteria(), rejections);
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

        AudienceFitResult result = new AudienceFitResult(
                verdict,
                score,
                Texts.truncate(Texts.trimToNull(draft.summary()), MAX_SUMMARY_LENGTH),
                criteria,
                validateIssues(draft.issues(), fragments, rejections),
                validateStrengths(draft.strengths(), fragments, rejections),
                profileConfidence,
                material.truncated()
        );
        return new Result(result, List.copyOf(rejections));
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
