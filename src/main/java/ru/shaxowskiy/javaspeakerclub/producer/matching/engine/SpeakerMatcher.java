package ru.shaxowskiy.javaspeakerclub.producer.matching.engine;

import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard.Criterion;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventParamKey;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.FeedbackFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.TalkFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation.Confidence;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation.Group;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.SpeakerMatchResult.Candidate;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.SpeakerMatchResult.Excluded;
import ru.shaxowskiy.javaspeakerclub.producer.matching.profile.ExpertProfileBuilder;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.TermMatcher;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.TermMatcher.Term;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.Texts;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Подбор спикеров под мероприятие (п. 10–12 ТЗ, US-04 — US-06). Чистая функция: на входе карточка мероприятия
 * и факты об экспертах, на выходе группы с объяснениями. Совпадение тега само по себе ничего не решает:
 * тематика — один из восьми факторов, обязательные требования проверяются отдельно, уверенность считается
 * отдельно от релевантности и падает, когда данных не хватает.
 */
public final class SpeakerMatcher {

    static final double HIGH_RELEVANCE = 0.6;
    static final double POTENTIAL_RELEVANCE = 0.35;
    static final double CLOSE_GAP = 0.05;

    private static final String MET = "met";
    private static final String NOT_MET = "not_met";
    private static final String UNKNOWN = "unknown";

    private SpeakerMatcher() {
    }

    /** Внутренние коэффициенты: только для журнала запусков, пользователю не показываются (п. 26 ТЗ). */
    public record Diagnostics(long expertId, String group, double relevance, double confidence, Map<String, Double> factors) {
    }

    public record Outcome(
            List<String> eventSummary,
            List<String> notes,
            List<Group<Candidate>> groups,
            List<Excluded> excluded,
            int considered,
            List<Diagnostics> diagnostics
    ) {
        public List<Long> recommendedIds() {
            return groups.stream().flatMap(group -> group.items().stream()).map(Candidate::expertId).toList();
        }
    }

    private record CriterionCheck(Criterion criterion, String status, String reason) {
    }

    private record Scored(ExpertFacts facts, Candidate candidate, String group, double relevance, double confidence,
                          Map<String, Double> factors, Contrast contrast) {
    }

    /** Факты для объяснения различий между близкими кандидатами (US-06). */
    private record Contrast(int relevantTalks, LocalDate lastTalk, int relevantReviews, double reviewAverage,
                              int organizerReviews, boolean sameCity, String confidenceLevel) {
    }

    public static Outcome match(EventCard card, List<ExpertFacts> experts, Map<Long, ExpertProfile> profiles,
                                LocalDate today, int maxCandidates) {
        List<Term> terms = eventTerms(card);
        List<String> unanswered = card.questions().stream()
                .filter(question -> !"answered".equals(question.status()))
                .map(question -> EventParamKey.byKey(question.key()).map(EventParamKey::label).orElse(question.key()))
                .toList();
        List<String> notes = notes(card, terms, unanswered);
        List<String> summary = card.summary();
        if (terms.isEmpty()) {
            return new Outcome(summary, notes, List.of(), List.of(), experts.size(), List.of());
        }

        List<Scored> scored = new ArrayList<>();
        List<Excluded> excluded = new ArrayList<>();
        for (ExpertFacts facts : experts) {
            if (facts.signals().irrelevantForEvents().contains(card.id())) {
                excluded.add(new Excluded(facts.name(),
                        List.of("Организатор отметил кандидата как неподходящего для этого мероприятия."), facts.id()));
                continue;
            }
            evaluate(card, terms, unanswered, facts, profiles.get(facts.id()), today, scored, excluded);
        }

        Map<String, List<Scored>> byGroup = new LinkedHashMap<>();
        Stream.of(Recommendation.GROUP_HIGH, Recommendation.GROUP_POTENTIAL, Recommendation.GROUP_NEEDS_CHECK)
                .forEach(code -> byGroup.put(code, new ArrayList<>()));
        scored.stream()
                .sorted(Comparator.comparingDouble(Scored::relevance).reversed().thenComparing(Scored::confidence, Comparator.reverseOrder()))
                .limit(maxCandidates)
                .forEach(item -> byGroup.get(item.group()).add(item));

        List<Group<Candidate>> groups = new ArrayList<>();
        byGroup.forEach((code, items) -> {
            if (!items.isEmpty()) {
                groups.add(new Group<>(Recommendation.groupTitle(code), items.stream().map(Scored::candidate).toList(),
                        comparisons(items), code, Recommendation.groupDescription(code)));
            }
        });
        List<Diagnostics> diagnostics = scored.stream()
                .map(item -> new Diagnostics(item.facts().id(), item.group(), round(item.relevance()),
                        round(item.confidence()), item.factors()))
                .toList();
        return new Outcome(summary, notes, groups, excluded, experts.size(), diagnostics);
    }

    private static void evaluate(EventCard card, List<Term> terms, List<String> unanswered, ExpertFacts facts,
                                 ExpertProfile profile, LocalDate today, List<Scored> scored, List<Excluded> excluded) {
        List<Coverage.Hit> hits = Coverage.compute(terms, facts, profile);
        double topic = Coverage.topicScore(hits, today);
        if (topic == 0) {
            return;
        }
        List<TalkFact> relevantTalks = Coverage.relevantTalks(hits);
        LocalDate lastTalk = relevantTalks.isEmpty() ? null : relevantTalks.get(0).date();

        List<String> why = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> matched = new ArrayList<>();

        explainTopics(hits, relevantTalks, lastTalk, today, why, limitations);

        double format = formatFit(card, facts, why, limitations);
        Set<String> relevantIds = new LinkedHashSet<>(relevantTalks.stream().map(TalkFact::id).toList());
        List<FeedbackFact> reviews = facts.feedback().stream().filter(item -> relevantIds.contains(item.talkId())).toList();
        double reviewScore = reviewScore(reviews, today);
        boolean contradictory = ExpertProfileBuilder.contradictory(reviews);
        String influence = explainReviews(reviews, reviewScore, contradictory, relevantTalks, why, limitations, missing);

        List<CriterionCheck> checks = card.criteria().stream().map(criterion -> check(criterion, card, facts, hits, terms, profile)).toList();
        Set<String> checkedKinds = new LinkedHashSet<>(card.criteria().stream().map(Criterion::check).toList());
        double logistics = logistics(card, facts, checkedKinds, why, limitations, missing);
        double history = facts.signals().positive() == 0 ? 0.5 : Math.min(1.0, 0.5 + 0.25 * facts.signals().positive());
        if (facts.signals().positive() > 0) {
            why.add("Есть история на платформе: принятые приглашения и выступления — "
                    + facts.signals().positive() + ".");
        }
        double interests = interestFit(facts, terms, why);

        List<String> notMet = new ArrayList<>();
        List<String> unknownMandatory = new ArrayList<>();
        for (CriterionCheck check : checks) {
            boolean mandatory = "mandatory".equals(check.criterion().kind());
            String text = check.criterion().text();
            switch (check.status()) {
                case MET -> matched.add((mandatory ? "Обязательно: " : "Желательно: ") + text + " — " + check.reason() + ".");
                case NOT_MET -> {
                    if (mandatory) {
                        notMet.add("Не выполнено обязательное требование «" + text + "»: " + check.reason() + ".");
                    } else {
                        limitations.add("Желательно «" + text + "» — не выполнено: " + check.reason() + ".");
                    }
                }
                default -> {
                    if (mandatory) {
                        unknownMandatory.add(text);
                        missing.add("Не проверено обязательное требование «" + text + "»: " + check.reason() + ".");
                    } else {
                        missing.add("Не проверено желательное требование «" + text + "»: " + check.reason() + ".");
                    }
                }
            }
        }
        if (!notMet.isEmpty()) {
            excluded.add(new Excluded(facts.name(), notMet, facts.id()));
            return;
        }

        double relevance = 0.40 * topic + 0.12 * Coverage.depth(relevantTalks.size()) + 0.10 * Coverage.recency(lastTalk, today)
                + 0.10 * format + 0.10 * reviewScore + 0.08 * logistics + 0.05 * history + 0.05 * interests;

        List<String> reasons = new ArrayList<>();
        double confidence = confidence(unanswered, relevantTalks, lastTalk, today, unknownMandatory, facts, contradictory, reasons);
        String level = Recommendation.confidenceLevel(confidence);
        String group = group(relevance, level, unknownMandatory, relevantTalks);

        Map<String, Double> factors = new LinkedHashMap<>();
        factors.put("topic", round(topic));
        factors.put("depth", Coverage.depth(relevantTalks.size()));
        factors.put("recency", Coverage.recency(lastTalk, today));
        factors.put("format", round(format));
        factors.put("reviews", round(reviewScore));
        factors.put("logistics", round(logistics));
        factors.put("history", round(history));
        factors.put("interests", round(interests));

        Candidate candidate = new Candidate(
                facts.name(),
                role(facts),
                null,
                why,
                limitations,
                missing,
                new Confidence(level, reasons),
                matched,
                keyExpertise(hits, profile),
                Coverage.talkRefs(relevantTalks, hits, 3),
                influence,
                facts.region(),
                facts.id()
        );
        int organizerReviews = (int) reviews.stream().filter(item -> "organizer".equals(item.authorType())).count();
        double average = reviews.stream().mapToInt(FeedbackFact::rating).average().orElse(0);
        String eventCity = card.value(EventParamKey.CITY);
        boolean sameCity = eventCity != null && sameCity(facts.region(), eventCity);
        scored.add(new Scored(facts, candidate, group, relevance, confidence, factors,
                new Contrast(relevantTalks.size(), lastTalk, reviews.size(), average, organizerReviews, sameCity, level)));
    }

    static List<Term> eventTerms(EventCard card) {
        List<String> phrases = new ArrayList<>();
        phrases.addAll(card.values(EventParamKey.TOPICS));
        phrases.addAll(card.values(EventParamKey.SUBTOPICS));
        phrases.addAll(card.tags());
        card.criteria().stream()
                .filter(criterion -> List.of("competency", "industry", "topic").contains(criterion.check()))
                .map(criterion -> criterion.value() == null || criterion.value().isBlank() ? criterion.text() : criterion.value())
                .forEach(phrases::add);
        Map<String, Term> unique = new LinkedHashMap<>();
        TermMatcher.terms(phrases).forEach(term -> unique.putIfAbsent(term.label().toLowerCase(Locale.ROOT), term));
        return List.copyOf(unique.values());
    }

    private static void explainTopics(List<Coverage.Hit> hits, List<TalkFact> relevantTalks, LocalDate lastTalk, LocalDate today,
                                      List<String> why, List<String> limitations) {
        List<String> confirmed = Coverage.labels(hits, ExpertProfile.CONFIRMED);
        if (!confirmed.isEmpty()) {
            TalkFact latest = relevantTalks.get(0);
            why.add("Подтверждённый опыт по темам мероприятия " + Texts.quoted(confirmed) + ": "
                    + Texts.talks(relevantTalks.size()) + ", последний — " + Texts.date(latest.date())
                    + " («" + latest.topic() + "», " + latest.event() + ").");
        }
        List<String> claimed = hits.stream()
                .filter(hit -> ExpertProfile.POTENTIAL.equals(hit.level()))
                .map(hit -> "«" + hit.term().label() + "» (" + hit.claimedSource() + ")")
                .toList();
        if (!claimed.isEmpty()) {
            limitations.add("Опыт по " + String.join(", ", claimed) + " заявлен, но не подтверждён выступлениями.");
        }
        List<String> adjacent = Coverage.labels(hits, ExpertProfile.ADJACENT);
        if (!adjacent.isEmpty()) {
            limitations.add("По " + Texts.quoted(adjacent) + " — только смежный опыт, прямых подтверждений нет.");
        }
        if (lastTalk != null && Coverage.daysSince(lastTalk, today) > 365) {
            limitations.add("Последний доклад по теме — " + Texts.date(lastTalk) + ": опыт менее актуален.");
        }
    }

    private static double formatFit(EventCard card, ExpertFacts facts, List<String> why, List<String> limitations) {
        String attendance = card.value(EventParamKey.ATTENDANCE);
        String eventFormat = card.value(EventParamKey.EVENT_FORMAT);
        String expectedFormat = "offline".equals(attendance) ? "OFFLINE" : "online".equals(attendance) ? "ONLINE" : null;
        String expectedType = "conference".equals(eventFormat) ? "CONFERENCE" : "meetup".equals(eventFormat) ? "MEETUP" : null;
        if ("webinar".equals(eventFormat) && expectedFormat == null) {
            expectedFormat = "ONLINE";
        }
        if (expectedFormat == null && expectedType == null) {
            return 0.5;
        }
        String finalFormat = expectedFormat;
        long matching = facts.talks().stream()
                .filter(talk -> finalFormat == null || finalFormat.equalsIgnoreCase(talk.format()))
                .filter(talk -> expectedType == null || expectedType.equalsIgnoreCase(talk.conferenceType()))
                .count();
        String described = Stream.of(expectedFormat == null ? null : Texts.format(expectedFormat),
                        expectedType == null ? null : Texts.conferenceType(expectedType))
                .filter(value -> value != null)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        if (matching > 0) {
            why.add("Есть выступления в нужном формате (" + described + "): " + Texts.talks(matching) + ".");
            return 1.0;
        }
        if (!facts.talks().isEmpty()) {
            limitations.add("На платформе нет его выступлений в формате «" + described + "».");
            return 0.4;
        }
        return 0.5;
    }

    /**
     * Отзывы только по докладам на темы мероприятия (п. 8–9 ТЗ): отзыв организатора весит больше,
     * свежий — больше старого, единичный отзыв не сдвигает оценку сильно, отсутствие отзывов не штрафуется.
     */
    static double reviewScore(List<FeedbackFact> reviews, LocalDate today) {
        if (reviews.isEmpty()) {
            return 0.5;
        }
        double weighted = 0;
        double total = 0;
        for (FeedbackFact review : reviews) {
            double weight = switch (review.authorType() == null ? "" : review.authorType()) {
                case "organizer" -> 1.5;
                case "participant" -> 1.0;
                default -> 0.7;
            };
            if (review.date() != null && review.date().isBefore(today.minusMonths(12))) {
                weight *= 0.6;
            }
            weighted += weight * review.rating();
            total += weight;
        }
        double average = weighted / total / 10.0;
        return reviews.size() == 1 ? 0.5 + (average - 0.5) / 2 : average;
    }

    private static String explainReviews(List<FeedbackFact> reviews, double score, boolean contradictory, List<TalkFact> relevantTalks,
                                         List<String> why, List<String> limitations, List<String> missing) {
        if (reviews.isEmpty()) {
            if (!relevantTalks.isEmpty()) {
                missing.add("Нет отзывов по докладам на темы мероприятия.");
            }
            return null;
        }
        long organizers = reviews.stream().filter(item -> "organizer".equals(item.authorType())).count();
        double average = reviews.stream().mapToInt(FeedbackFact::rating).average().orElse(0);
        if (reviews.size() >= 2 && score >= 0.75 && !contradictory) {
            why.add("Слушатели высоко оценили доклады на эту тему: " + Texts.plural(reviews.size(), "отзыв", "отзыва", "отзывов")
                    + ", средняя оценка " + Texts.rating(average)
                    + (organizers > 0 ? ", в том числе от организаторов — " + organizers : "") + ".");
        }
        if (contradictory) {
            limitations.add("Отзывы по теме противоречивы: есть и высокие, и низкие оценки — нужен взгляд на контекст.");
        }
        reviews.stream()
                .filter(item -> item.rating() <= 4)
                .findFirst()
                .ifPresent(item -> limitations.add("Есть критический отзыв (" + item.rating() + "/10): «"
                        + shorten(item.negative()) + "». Один отзыв не исключает кандидата."));
        return "Учтены только отзывы по докладам на темы мероприятия: " + reviews.size()
                + (organizers > 0 ? " (от организаторов — " + organizers + ")" : "")
                + ", средняя оценка " + Texts.rating(average) + ". Отзывы по другим темам на подбор не влияли.";
    }

    private static CriterionCheck check(Criterion criterion, EventCard card, ExpertFacts facts, List<Coverage.Hit> hits,
                                        List<Term> terms, ExpertProfile profile) {
        String value = criterion.value() == null || criterion.value().isBlank() ? null : criterion.value();
        ExpertFacts.Preferences preferences = facts.preferences();
        return switch (criterion.check() == null ? "other" : criterion.check()) {
            case "language" -> {
                String language = value != null ? value : card.value(EventParamKey.LANGUAGE);
                if (language == null) {
                    yield new CriterionCheck(criterion, UNKNOWN, "язык не указан в требованиях");
                }
                if (preferences.languages().isEmpty()) {
                    yield new CriterionCheck(criterion, UNKNOWN, "языки выступлений в профиле не указаны");
                }
                boolean speaks = preferences.languages().stream().anyMatch(item -> sameLanguage(item, language));
                yield speaks
                        ? new CriterionCheck(criterion, MET, "выступает на языке «" + language + "»")
                        : new CriterionCheck(criterion, NOT_MET, "в профиле указаны языки " + Texts.quoted(preferences.languages()));
            }
            case "city" -> {
                String city = value != null ? value : card.value(EventParamKey.CITY);
                if (city == null || facts.region() == null) {
                    yield new CriterionCheck(criterion, UNKNOWN, "город не указан");
                }
                if (sameCity(facts.region(), city)) {
                    yield new CriterionCheck(criterion, MET, "живёт в городе «" + city + "»");
                }
                yield Boolean.FALSE.equals(preferences.offlineOk())
                        ? new CriterionCheck(criterion, NOT_MET, "живёт в городе «" + facts.region() + "» и не выступает офлайн")
                        : new CriterionCheck(criterion, UNKNOWN, "живёт в городе «" + facts.region() + "», готовность приехать не указана");
            }
            case "attendance" -> attendanceCheck(criterion, value != null ? value : card.value(EventParamKey.ATTENDANCE), preferences);
            case "competency", "industry", "topic" -> {
                Term term = TermMatcher.term(value != null ? value : criterion.text());
                Coverage.Hit hit = hits.stream()
                        .filter(item -> item.term().label().equalsIgnoreCase(term.label()))
                        .findFirst()
                        .orElseGet(() -> Coverage.compute(List.of(term), facts, profile).get(0));
                String level = hit.level();
                if (ExpertProfile.CONFIRMED.equals(level)) {
                    yield new CriterionCheck(criterion, MET, "подтверждено докладами: " + hit.talks().size());
                }
                if (ExpertProfile.POTENTIAL.equals(level)) {
                    yield new CriterionCheck(criterion, UNKNOWN, "заявлено (" + hit.claimedSource() + "), но не подтверждено докладами");
                }
                if (ExpertProfile.ADJACENT.equals(level)) {
                    yield new CriterionCheck(criterion, UNKNOWN, "есть только смежный опыт");
                }
                yield new CriterionCheck(criterion, UNKNOWN, "нет данных, чтобы проверить");
            }
            case "format" -> {
                String wanted = (value != null ? value : criterion.text()).toLowerCase(Locale.ROOT);
                boolean has = facts.talks().stream().anyMatch(talk ->
                        wanted.contains(Texts.conferenceType(talk.conferenceType())) || wanted.contains(Texts.format(talk.format()))
                                || wanted.contains(String.valueOf(talk.conferenceType()).toLowerCase(Locale.ROOT))
                                || wanted.contains(String.valueOf(talk.format()).toLowerCase(Locale.ROOT)));
                yield has
                        ? new CriterionCheck(criterion, MET, "есть выступления в этом формате")
                        : new CriterionCheck(criterion, UNKNOWN, "на платформе нет выступлений в этом формате — данных нет");
            }
            case "audience" -> new CriterionCheck(criterion, UNKNOWN, "уровень аудитории прошлых выступлений не хранится");
            default -> new CriterionCheck(criterion, UNKNOWN, "требование проверяется вручную");
        };
    }

    private static CriterionCheck attendanceCheck(Criterion criterion, String attendance, ExpertFacts.Preferences preferences) {
        if (attendance == null) {
            return new CriterionCheck(criterion, UNKNOWN, "формат участия не указан");
        }
        Boolean ok = "online".equals(attendance) ? preferences.onlineOk() : "offline".equals(attendance) ? preferences.offlineOk() : null;
        String described = "online".equals(attendance) ? "онлайн" : "offline".equals(attendance) ? "офлайн" : "гибридно";
        if (ok == null) {
            return new CriterionCheck(criterion, UNKNOWN, "в профиле не указано, выступает ли он " + described);
        }
        return ok
                ? new CriterionCheck(criterion, MET, "выступает " + described)
                : new CriterionCheck(criterion, NOT_MET, "в профиле указано, что не выступает " + described);
    }

    private static double logistics(EventCard card, ExpertFacts facts, Set<String> checkedKinds,
                                    List<String> why, List<String> limitations, List<String> missing) {
        List<Double> parts = new ArrayList<>();
        String language = card.value(EventParamKey.LANGUAGE);
        if (language != null) {
            List<String> languages = facts.preferences().languages();
            boolean explain = !checkedKinds.contains("language");
            if (languages.isEmpty()) {
                parts.add(0.5);
                if (explain) {
                    missing.add("Языки выступлений не указаны — язык «" + language + "» не проверен.");
                }
            } else if (languages.stream().anyMatch(item -> sameLanguage(item, language))) {
                parts.add(1.0);
                if (explain) {
                    why.add("Выступает на нужном языке: " + language + ".");
                }
            } else {
                parts.add(0.0);
                if (explain) {
                    limitations.add("В профиле указаны языки " + Texts.quoted(languages) + ", а нужен «" + language + "».");
                }
            }
        }
        String city = card.value(EventParamKey.CITY);
        String attendance = card.value(EventParamKey.ATTENDANCE);
        if (city != null && !"online".equals(attendance) && facts.region() != null) {
            boolean explain = !checkedKinds.contains("city");
            if (sameCity(facts.region(), city)) {
                parts.add(1.0);
                if (explain) {
                    why.add("Живёт в городе мероприятия: " + city + ".");
                }
            } else {
                parts.add(0.4);
                if (explain) {
                    limitations.add("Живёт в другом городе (" + facts.region() + "), мероприятие проходит в городе " + city + ".");
                }
            }
        }
        return parts.isEmpty() ? 0.5 : parts.stream().mapToDouble(Double::doubleValue).average().orElse(0.5);
    }

    private static double interestFit(ExpertFacts facts, List<Term> terms, List<String> why) {
        List<String> matched = facts.preferences().interests().stream()
                .filter(interest -> terms.stream().anyMatch(term -> TermMatcher.matches(term, TermMatcher.keys(interest))))
                .toList();
        if (matched.isEmpty()) {
            return 0.5;
        }
        why.add("Тема совпадает с интересами эксперта: " + Texts.quoted(matched) + ".");
        return 1.0;
    }

    private static double confidence(List<String> unanswered, List<TalkFact> relevantTalks, LocalDate lastTalk, LocalDate today,
                                     List<String> unknownMandatory, ExpertFacts facts, boolean contradictory, List<String> reasons) {
        double score = 1.0;
        if (!unanswered.isEmpty()) {
            score -= Math.min(0.2, 0.1 * unanswered.size());
            reasons.add("Организатор не уточнил: " + String.join(", ", unanswered).toLowerCase(Locale.ROOT) + ".");
        }
        if (relevantTalks.isEmpty()) {
            score -= 0.3;
            reasons.add("Опыт по теме не подтверждён выступлениями.");
        } else if (Coverage.daysSince(lastTalk, today) > 730) {
            score -= 0.15;
            reasons.add("Последнее выступление по теме — больше двух лет назад.");
        }
        if (!unknownMandatory.isEmpty()) {
            score -= Math.min(0.3, 0.15 * unknownMandatory.size());
            reasons.add("Не проверены обязательные требования: " + Texts.quoted(unknownMandatory) + ".");
        }
        List<String> gaps = new ArrayList<>();
        if (facts.about() == null || facts.about().isBlank()) {
            gaps.add("нет описания опыта");
        }
        if (facts.companyRole() == null || facts.companyRole().isBlank()) {
            gaps.add("не указана должность");
        }
        if (facts.preferences().languages().isEmpty()) {
            gaps.add("не указаны языки");
        }
        if (!gaps.isEmpty()) {
            score -= Math.min(0.15, 0.05 * gaps.size());
            reasons.add("Профиль заполнен не полностью: " + String.join(", ", gaps) + ".");
        }
        if (contradictory) {
            score -= 0.1;
            reasons.add("Отзывы по теме противоречивы.");
        }
        if (reasons.isEmpty()) {
            reasons.add("Опыт подтверждён свежими выступлениями, обязательные требования проверены.");
        }
        return Math.max(0, score);
    }

    private static String group(double relevance, String level, List<String> unknownMandatory, List<TalkFact> relevantTalks) {
        if (Recommendation.CONFIDENCE_LOW.equals(level)) {
            return Recommendation.GROUP_NEEDS_CHECK;
        }
        if (relevance >= HIGH_RELEVANCE && unknownMandatory.isEmpty() && !relevantTalks.isEmpty()) {
            return Recommendation.GROUP_HIGH;
        }
        return relevance >= POTENTIAL_RELEVANCE ? Recommendation.GROUP_POTENTIAL : Recommendation.GROUP_NEEDS_CHECK;
    }

    /** Близкие кандидаты: никто не объявляется «лучшим», вместо этого — чем они отличаются (US-06, п. 11 ТЗ). */
    private static List<String> comparisons(List<Scored> items) {
        List<String> comparisons = new ArrayList<>();
        for (int i = 0; i + 1 < items.size(); i++) {
            Scored a = items.get(i);
            Scored b = items.get(i + 1);
            if (a.relevance() - b.relevance() < CLOSE_GAP) {
                comparisons.add(compare(a, b));
            }
        }
        return comparisons;
    }

    private static String compare(Scored a, Scored b) {
        String nameA = a.facts().name();
        String nameB = b.facts().name();
        Contrast x = a.contrast();
        Contrast y = b.contrast();
        List<String> differences = new ArrayList<>();
        if (x.relevantTalks() != y.relevantTalks()) {
            boolean aMore = x.relevantTalks() > y.relevantTalks();
            differences.add((aMore ? nameA : nameB) + " — больше докладов по теме ("
                    + Math.max(x.relevantTalks(), y.relevantTalks()) + " против " + Math.min(x.relevantTalks(), y.relevantTalks()) + ")");
        }
        if (x.lastTalk() != null && y.lastTalk() != null && Math.abs(x.lastTalk().toEpochDay() - y.lastTalk().toEpochDay()) > 90) {
            boolean aFresher = x.lastTalk().isAfter(y.lastTalk());
            differences.add((aFresher ? nameA : nameB) + " — более свежий опыт (последний доклад "
                    + Texts.date(aFresher ? x.lastTalk() : y.lastTalk()) + " против " + Texts.date(aFresher ? y.lastTalk() : x.lastTalk()) + ")");
        }
        if ((x.relevantReviews() == 0) != (y.relevantReviews() == 0)) {
            differences.add((x.relevantReviews() > 0 ? nameA : nameB) + " — есть отзывы по докладам на эту тему, "
                    + (x.relevantReviews() > 0 ? nameB : nameA) + " — нет");
        } else if (x.relevantReviews() > 0 && Math.abs(x.reviewAverage() - y.reviewAverage()) >= 0.5) {
            boolean aHigher = x.reviewAverage() > y.reviewAverage();
            differences.add((aHigher ? nameA : nameB) + " — выше оценки слушателей по теме ("
                    + Texts.rating(Math.max(x.reviewAverage(), y.reviewAverage())) + " против "
                    + Texts.rating(Math.min(x.reviewAverage(), y.reviewAverage())) + ")");
        }
        if ((x.organizerReviews() > 0) != (y.organizerReviews() > 0)) {
            differences.add((x.organizerReviews() > 0 ? nameA : nameB) + " — есть отзывы организаторов");
        }
        if (x.sameCity() != y.sameCity()) {
            differences.add((x.sameCity() ? nameA : nameB) + " — живёт в городе мероприятия");
        }
        if (!x.confidenceLevel().equals(y.confidenceLevel())) {
            differences.add((rank(x.confidenceLevel()) > rank(y.confidenceLevel()) ? nameA : nameB) + " — данных больше, уверенность выше");
        }
        if (differences.isEmpty()) {
            return nameA + " и " + nameB + " близки по соответствию и по ключевым фактам — выбор остаётся за организатором.";
        }
        return nameA + " и " + nameB + " близки по соответствию. Отличия: "
                + String.join("; ", differences.subList(0, Math.min(3, differences.size()))) + ".";
    }

    private static List<String> keyExpertise(List<Coverage.Hit> hits, ExpertProfile profile) {
        List<String> result = new ArrayList<>();
        Set<String> used = new LinkedHashSet<>();
        for (ExpertProfile.ExpertiseArea area : profile.expertise()) {
            if (result.size() >= 5) {
                break;
            }
            boolean relevant = hits.stream().anyMatch(hit -> TermMatcher.matches(hit.term(), TermMatcher.keys(area.area())));
            if (!relevant || !used.add(area.area())) {
                continue;
            }
            result.add(describe(area));
        }
        for (ExpertProfile.ExpertiseArea area : profile.expertise()) {
            if (result.size() >= 5) {
                break;
            }
            if (ExpertProfile.CONFIRMED.equals(area.level()) && used.add(area.area())) {
                result.add(describe(area));
            }
        }
        return result;
    }

    private static String describe(ExpertProfile.ExpertiseArea area) {
        return switch (area.level()) {
            case ExpertProfile.CONFIRMED -> area.area() + " — подтверждена (" + Texts.talks(area.evidence().size()) + ")";
            case ExpertProfile.POTENTIAL -> area.area() + " — заявлена, не подтверждена";
            case ExpertProfile.ADJACENT -> area.area() + " — смежная";
            default -> area.area() + " — недостаточно данных";
        };
    }

    private static List<String> notes(EventCard card, List<Term> terms, List<String> unanswered) {
        List<String> notes = new ArrayList<>();
        if (terms.isEmpty()) {
            notes.add("Не указана тематика мероприятия — подбор невозможен. Ответьте на вопрос о темах или добавьте их в карточку.");
        }
        if (!unanswered.isEmpty()) {
            notes.add("Без ответа на уточняющие вопросы (" + String.join(", ", unanswered).toLowerCase(Locale.ROOT)
                    + ") — уверенность рекомендаций снижена.");
        }
        if (!card.changesSinceLastMatching().isEmpty()) {
            notes.add("Подбор выполнен с учётом изменённых требований: " + String.join("; ", card.changesSinceLastMatching()) + ".");
        }
        if (card.known(EventParamKey.AUDIENCE_LEVEL)) {
            notes.add("Уровень аудитории прошлых выступлений на платформе не хранится — соответствие уровню «"
                    + EventParamKey.AUDIENCE_LEVEL.display(card.values(EventParamKey.AUDIENCE_LEVEL)) + "» не проверялось.");
        }
        List<String> interpreted = card.criteria().stream()
                .filter(criterion -> criterion.interpretation() && "mandatory".equals(criterion.kind()))
                .map(Criterion::text)
                .toList();
        if (!interpreted.isEmpty()) {
            notes.add("Обязательность требований " + Texts.quoted(interpreted)
                    + " определил AI по тексту — это интерпретация, а не подтверждённый факт. Проверьте её в карточке.");
        }
        return notes;
    }

    static boolean sameCity(String region, String city) {
        if (region == null || city == null) {
            return false;
        }
        String a = TermMatcher.normalize(region);
        String b = TermMatcher.normalize(city);
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private static boolean sameLanguage(String a, String b) {
        return TermMatcher.normalize(a).startsWith(TermMatcher.normalize(b).substring(0, Math.min(3, b.length())));
    }

    private static String role(ExpertFacts facts) {
        if (facts.companyRole() == null || facts.companyRole().isBlank()) {
            return facts.company();
        }
        return facts.company() == null || facts.company().isBlank() || "Unknown".equals(facts.company())
                ? facts.companyRole()
                : facts.companyRole() + ", " + facts.company();
    }

    private static String shorten(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 120 ? text.substring(0, 117) + "…" : text;
    }

    private static int rank(String level) {
        return switch (level) {
            case Recommendation.CONFIDENCE_HIGH -> 2;
            case Recommendation.CONFIDENCE_MEDIUM -> 1;
            default -> 0;
        };
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
