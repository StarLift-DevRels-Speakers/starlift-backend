package ru.shaxowskiy.javaspeakerclub.producer.matching.profile;

import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.FeedbackFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.TalkFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile.Evidence;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile.ExpertiseArea;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile.Gap;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.TermMatcher;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.Texts;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Профиль эксперта из фактов (US-10, US-11). Экспертиза классифицируется по п. 7 ТЗ: подтверждённая — по докладам,
 * потенциальная — по профилю, описанию опыта и должности, смежная — по связям тегов платформы, «недостаточно данных» —
 * интересы без оснований. Потенциальная и смежная экспертиза никогда не становятся подтверждённой.
 */
public final class ExpertProfileBuilder {

    private static final int MAX_ADJACENT = 6;
    private static final int RECENT_TALKS = 5;
    private static final int RECENT_REVIEWS = 5;

    private ExpertProfileBuilder() {
    }

    public static ExpertProfile build(ExpertFacts facts, TagCooccurrence cooccurrence, List<String> vocabulary, LocalDate today) {
        Map<String, ExpertiseArea> areas = new LinkedHashMap<>();
        confirmed(facts, areas);
        potential(facts, vocabulary, areas);
        adjacent(cooccurrence, areas);
        insufficient(facts, areas);

        List<ExpertiseArea> expertise = areas.values().stream()
                .sorted(Comparator.comparingInt((ExpertiseArea area) -> levelOrder(area.level()))
                        .thenComparing((ExpertiseArea area) -> -area.evidence().size())
                        .thenComparing(ExpertiseArea::area))
                .toList();

        List<Gap> gaps = gaps(facts, today);
        return new ExpertProfile(
                new ExpertProfile.Expert(facts.id(), facts.name(), facts.companyRole(), facts.company(), facts.region(), facts.stage()),
                expertise,
                publicExperience(facts, today),
                feedbackSummary(facts, today),
                new ExpertProfile.PlatformHistory(facts.signals().acceptedInvitations(), facts.signals().talksGiven(),
                        facts.signals().repeatInvitations()),
                facts.preferences(),
                gaps,
                gaps.size() <= 1 ? "high" : gaps.size() <= 3 ? "medium" : "low"
        );
    }

    private static void confirmed(ExpertFacts facts, Map<String, ExpertiseArea> areas) {
        Map<String, List<TalkFact>> byTag = new TreeMap<>();
        for (TalkFact talk : facts.talks()) {
            for (String tag : talk.tags()) {
                byTag.computeIfAbsent(TermMatcher.normalizeTag(tag), key -> new ArrayList<>()).add(talk);
            }
        }
        byTag.forEach((tag, talks) -> {
            List<TalkFact> sorted = talks.stream()
                    .sorted(Comparator.comparing(TalkFact::date, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            List<Evidence> evidence = sorted.stream()
                    .map(talk -> new Evidence("talk", talk.id(), "«" + talk.topic() + "» — " + talk.event(), talk.date()))
                    .toList();
            areas.put(tag, new ExpertiseArea(tag, ExpertProfile.CONFIRMED, evidence, sorted.get(0).date()));
        });
    }

    private static void potential(ExpertFacts facts, List<String> vocabulary, Map<String, ExpertiseArea> areas) {
        for (String tag : facts.profileTags()) {
            String area = TermMatcher.normalizeTag(tag);
            if (!areas.containsKey(area)) {
                areas.put(area, new ExpertiseArea(area, ExpertProfile.POTENTIAL,
                        List.of(new Evidence("profile_tag", null, "указано в профиле", null)), null));
            }
        }
        Set<String> aboutKeys = TermMatcher.keys(facts.about());
        Set<String> roleKeys = TermMatcher.keys(facts.companyRole());
        for (String tag : vocabulary) {
            String area = TermMatcher.normalizeTag(tag);
            if (areas.containsKey(area)) {
                continue;
            }
            TermMatcher.Term term = TermMatcher.term(area);
            if (TermMatcher.matches(term, aboutKeys)) {
                areas.put(area, new ExpertiseArea(area, ExpertProfile.POTENTIAL,
                        List.of(new Evidence("about", null, "описание опыта: «" + snippet(facts.about(), term) + "»", null)), null));
            } else if (TermMatcher.matches(term, roleKeys)) {
                areas.put(area, new ExpertiseArea(area, ExpertProfile.POTENTIAL,
                        List.of(new Evidence("role", null, "должность: " + facts.companyRole(), null)), null));
            }
        }
    }

    private static void adjacent(TagCooccurrence cooccurrence, Map<String, ExpertiseArea> areas) {
        List<String> confirmed = areas.values().stream()
                .filter(area -> ExpertProfile.CONFIRMED.equals(area.level()))
                .map(ExpertiseArea::area)
                .toList();
        Map<String, Evidence> adjacent = new LinkedHashMap<>();
        for (String base : confirmed) {
            cooccurrence.related(base).forEach((tag, count) -> {
                if (!areas.containsKey(tag) && !adjacent.containsKey(tag)) {
                    adjacent.put(tag, new Evidence("cooccurrence", null,
                            "встречается вместе с «" + base + "» в докладах и CFP платформы: " + Texts.plural(count, "раз", "раза", "раз"),
                            null));
                }
            });
        }
        adjacent.entrySet().stream()
                .limit(MAX_ADJACENT)
                .forEach(entry -> areas.put(entry.getKey(),
                        new ExpertiseArea(entry.getKey(), ExpertProfile.ADJACENT, List.of(entry.getValue()), null)));
    }

    private static void insufficient(ExpertFacts facts, Map<String, ExpertiseArea> areas) {
        Set<String> known = new LinkedHashSet<>();
        areas.keySet().forEach(area -> known.addAll(TermMatcher.keys(area)));
        for (String interest : facts.preferences().interests()) {
            TermMatcher.Term term = TermMatcher.term(interest);
            String area = TermMatcher.normalizeTag(interest);
            if (!term.keys().isEmpty() && !areas.containsKey(area) && !TermMatcher.matches(term, known)) {
                areas.put(area, new ExpertiseArea(area, ExpertProfile.INSUFFICIENT_DATA,
                        List.of(new Evidence("interest", null, "указано в интересах, подтверждений и оснований нет", null)), null));
            }
        }
    }

    private static ExpertProfile.PublicExperience publicExperience(ExpertFacts facts, LocalDate today) {
        List<TalkFact> past = facts.talks().stream()
                .filter(talk -> talk.date() != null && !talk.date().isAfter(today))
                .sorted(Comparator.comparing(TalkFact::date).reversed())
                .toList();
        Map<String, Integer> byType = new TreeMap<>();
        Map<String, Integer> byFormat = new TreeMap<>();
        past.forEach(talk -> {
            byType.merge(Texts.conferenceType(talk.conferenceType()), 1, Integer::sum);
            byFormat.merge(Texts.format(talk.format()), 1, Integer::sum);
        });
        int last12 = (int) past.stream().filter(talk -> talk.date().isAfter(today.minusMonths(12))).count();
        return new ExpertProfile.PublicExperience(
                past.size(),
                last12,
                past.isEmpty() ? null : past.get(0).date(),
                byType,
                byFormat,
                past.stream().limit(RECENT_TALKS).toList()
        );
    }

    static ExpertProfile.FeedbackSummary feedbackSummary(ExpertFacts facts, LocalDate today) {
        List<FeedbackFact> recent = facts.feedback().stream()
                .filter(item -> item.date() == null || item.date().isAfter(today.minusMonths(12)))
                .toList();
        if (recent.isEmpty()) {
            return new ExpertProfile.FeedbackSummary(0, 0, 0, null, false, "Отзывов за последний год нет.", List.of());
        }
        long organizers = recent.stream().filter(item -> "organizer".equals(item.authorType())).count();
        long participants = recent.stream().filter(item -> "participant".equals(item.authorType())).count();
        double average = recent.stream().mapToInt(FeedbackFact::rating).average().orElse(0);
        boolean contradictory = contradictory(recent);
        String note = contradictory
                ? "Отзывы противоречивы: есть и высокие, и низкие оценки — смотрите контекст докладов."
                : "Отзывы учитываются только вместе с темой, форматом и мероприятием доклада.";
        Map<String, TalkFact> talks = new LinkedHashMap<>();
        facts.talks().forEach(talk -> talks.put(talk.id(), talk));
        List<ExpertProfile.Review> reviews = recent.stream()
                .sorted(Comparator.comparing(FeedbackFact::date, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_REVIEWS)
                .map(item -> review(item, talks.get(item.talkId())))
                .toList();
        return new ExpertProfile.FeedbackSummary(recent.size(), (int) organizers, (int) participants,
                Math.round(average * 10) / 10.0, contradictory, note, reviews);
    }

    private static ExpertProfile.Review review(FeedbackFact item, TalkFact talk) {
        List<String> parts = new ArrayList<>();
        if (item.positive() != null && !item.positive().isBlank()) {
            parts.add("понравилось: " + item.positive().trim());
        }
        if (item.negative() != null && !item.negative().isBlank()) {
            parts.add("не понравилось: " + item.negative().trim());
        }
        String comment = String.join("; ", parts);
        if (comment.length() > 160) {
            comment = comment.substring(0, 157) + "…";
        }
        return new ExpertProfile.Review(
                talk == null ? null : talk.topic(),
                talk == null ? null : talk.event(),
                talk == null ? null : talk.date(),
                talk == null ? null : Texts.format(talk.format()) + ", " + Texts.conferenceType(talk.conferenceType()),
                item.authorType(),
                item.rating(),
                comment.isEmpty() ? null : Texts.capitalize(comment),
                item.date());
    }

    /** Противоречие — минимум два высоких (9–10) и два низких (0–6) отзыва: усреднять такое механически нельзя. */
    public static boolean contradictory(List<FeedbackFact> feedback) {
        long high = feedback.stream().filter(item -> item.rating() >= 9).count();
        long low = feedback.stream().filter(item -> item.rating() <= 6).count();
        return high >= 2 && low >= 2;
    }

    static List<Gap> gaps(ExpertFacts facts, LocalDate today) {
        List<Gap> gaps = new ArrayList<>();
        if (facts.talks().isEmpty()) {
            gaps.add(new Gap("talks", "Нет выступлений на платформе: экспертиза не подтверждена ни одним докладом."));
        } else if (facts.talks().stream().map(TalkFact::date).filter(Objects::nonNull)
                .noneMatch(date -> date.isAfter(today.minusMonths(12)))) {
            gaps.add(new Gap("talks", "Нет выступлений за последний год: опыт считается менее актуальным."));
        }
        long untagged = facts.talks().stream().filter(talk -> talk.tags().isEmpty()).count();
        if (untagged > 0) {
            gaps.add(new Gap("talkTags", "У " + Texts.plural(untagged, "доклада", "докладов", "докладов")
                    + " нет тегов: по ним экспертиза не определяется."));
        }
        if (facts.about() == null || facts.about().isBlank()) {
            gaps.add(new Gap("about", "Нет описания опыта: проекты и заявленные компетенции не видны, подбор опирается только на доклады."));
        }
        if (facts.companyRole() == null || facts.companyRole().isBlank()) {
            gaps.add(new Gap("companyRole", "Не указана должность."));
        }
        if (facts.preferences().languages().isEmpty()) {
            gaps.add(new Gap("languages", "Не указаны языки выступлений: требования мероприятий к языку не проверить."));
        }
        if (facts.preferences().onlineOk() == null && facts.preferences().offlineOk() == null) {
            gaps.add(new Gap("attendance", "Не указано, выступаете ли вы онлайн и офлайн: требования к формату участия не проверить."));
        }
        return gaps;
    }

    private static String snippet(String text, TermMatcher.Term term) {
        String[] words = text.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            if (TermMatcher.matches(term, TermMatcher.keys(words[i]))) {
                int from = Math.max(0, i - 3);
                int to = Math.min(words.length, i + 4);
                String piece = String.join(" ", List.of(words).subList(from, to));
                return (from > 0 ? "…" : "") + piece + (to < words.length ? "…" : "");
            }
        }
        return text.length() > 60 ? text.substring(0, 60) + "…" : text;
    }

    private static int levelOrder(String level) {
        return switch (level) {
            case ExpertProfile.CONFIRMED -> 0;
            case ExpertProfile.POTENTIAL -> 1;
            case ExpertProfile.ADJACENT -> 2;
            default -> 3;
        };
    }
}
