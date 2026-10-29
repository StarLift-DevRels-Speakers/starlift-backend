package ru.shaxowskiy.javaspeakerclub.producer.matching.engine;

import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.TalkFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.TermMatcher;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.TermMatcher.Term;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.Texts;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Насколько опыт эксперта покрывает темы мероприятия. Для каждой темы берётся лучшее подтверждение:
 * доклад (свежий весит больше) → заявлено в профиле → смежная область → ничего.
 * Подтверждённый опыт всегда весит больше заявленного (п. 3 ТЗ, принципы 2 и 4).
 */
final class Coverage {

    private Coverage() {
    }

    /** Покрытие одной темы. claimedSource — где заявлено без подтверждения (профиль, описание опыта, должность). */
    record Hit(Term term, List<TalkFact> talks, String claimedSource, boolean adjacent) {

        String level() {
            if (!talks.isEmpty()) {
                return ExpertProfile.CONFIRMED;
            }
            if (claimedSource != null) {
                return ExpertProfile.POTENTIAL;
            }
            return adjacent ? ExpertProfile.ADJACENT : null;
        }

        LocalDate lastTalk() {
            return talks.stream().map(TalkFact::date).filter(date -> date != null).max(Comparator.naturalOrder()).orElse(null);
        }
    }

    static List<Hit> compute(List<Term> terms, ExpertFacts facts, ExpertProfile profile) {
        Map<String, Set<String>> talkKeys = new LinkedHashMap<>();
        for (TalkFact talk : facts.talks()) {
            Set<String> keys = new LinkedHashSet<>(TermMatcher.keys(talk.topic()));
            talk.tags().forEach(tag -> keys.addAll(TermMatcher.keys(tag)));
            talkKeys.put(talk.id(), keys);
        }
        Set<String> profileTagKeys = new LinkedHashSet<>();
        facts.profileTags().forEach(tag -> profileTagKeys.addAll(TermMatcher.keys(tag)));
        Set<String> aboutKeys = TermMatcher.keys(facts.about());
        Set<String> roleKeys = TermMatcher.keys(facts.companyRole());
        Set<String> adjacentKeys = new LinkedHashSet<>();
        profile.expertise().stream()
                .filter(area -> ExpertProfile.ADJACENT.equals(area.level()))
                .forEach(area -> adjacentKeys.addAll(TermMatcher.keys(area.area())));

        List<Hit> hits = new ArrayList<>();
        for (Term term : terms) {
            List<TalkFact> talks = facts.talks().stream()
                    .filter(talk -> TermMatcher.matches(term, talkKeys.get(talk.id())))
                    .toList();
            String claimed = null;
            if (TermMatcher.matches(term, profileTagKeys)) {
                claimed = "профиль";
            } else if (TermMatcher.matches(term, aboutKeys)) {
                claimed = "описание опыта";
            } else if (TermMatcher.matches(term, roleKeys)) {
                claimed = "должность";
            }
            hits.add(new Hit(term, talks, claimed, TermMatcher.matches(term, adjacentKeys)));
        }
        return hits;
    }

    static double weight(Hit hit, LocalDate today) {
        String level = hit.level();
        if (ExpertProfile.CONFIRMED.equals(level)) {
            long days = daysSince(hit.lastTalk(), today);
            return days <= 365 ? 1.0 : days <= 730 ? 0.8 : 0.6;
        }
        if (ExpertProfile.POTENTIAL.equals(level)) {
            return 0.35;
        }
        return ExpertProfile.ADJACENT.equals(level) ? 0.2 : 0.0;
    }

    static double topicScore(List<Hit> hits, LocalDate today) {
        return hits.isEmpty() ? 0 : hits.stream().mapToDouble(hit -> weight(hit, today)).average().orElse(0);
    }

    /** Доклады, подтверждающие хотя бы одну тему, от свежих к старым. */
    static List<TalkFact> relevantTalks(List<Hit> hits) {
        Map<String, TalkFact> talks = new LinkedHashMap<>();
        hits.forEach(hit -> hit.talks().forEach(talk -> talks.putIfAbsent(talk.id(), talk)));
        return talks.values().stream()
                .sorted(Comparator.comparing(TalkFact::date, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    static double depth(int relevantTalks) {
        return relevantTalks == 0 ? 0 : relevantTalks == 1 ? 0.5 : relevantTalks == 2 ? 0.75 : 1.0;
    }

    static double recency(LocalDate lastTalk, LocalDate today) {
        if (lastTalk == null) {
            return 0;
        }
        long days = daysSince(lastTalk, today);
        return days <= 365 ? 1.0 : days <= 730 ? 0.6 : 0.3;
    }

    static long daysSince(LocalDate date, LocalDate today) {
        return date == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(date, today);
    }

    static List<Recommendation.TalkRef> talkRefs(List<TalkFact> talks, List<Hit> hits, int limit) {
        return talks.stream()
                .limit(limit)
                .map(talk -> new Recommendation.TalkRef(
                        talk.id(),
                        talk.topic(),
                        talk.event(),
                        talk.date(),
                        Texts.format(talk.format()) + ", " + Texts.conferenceType(talk.conferenceType()),
                        hits.stream()
                                .filter(hit -> hit.talks().contains(talk))
                                .map(hit -> hit.term().label())
                                .toList()))
                .toList();
    }

    static List<String> labels(List<Hit> hits, String level) {
        return hits.stream().filter(hit -> level.equals(hit.level())).map(hit -> hit.term().label()).toList();
    }
}
