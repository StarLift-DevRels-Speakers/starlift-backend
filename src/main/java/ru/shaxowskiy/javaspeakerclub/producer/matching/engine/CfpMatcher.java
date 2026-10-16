package ru.shaxowskiy.javaspeakerclub.producer.matching.engine;

import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventParamKey;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventRecommendationResult;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.TalkFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation.Group;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.TermMatcher;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.TermMatcher.Term;
import ru.shaxowskiy.javaspeakerclub.producer.matching.text.Texts;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Подбор мероприятий и CFP для эксперта (п. 13–16 ТЗ, US-12, US-13). Строится на подтверждённой экспертизе,
 * а не на должности. Требования мероприятия берутся только из данных CFP; язык, формат и требования к спикеру,
 * которых в источнике нет, перечисляются как неизвестные, а не додумываются.
 */
public final class CfpMatcher {

    private static final List<String> STAGES = List.of("local", "regional", "federal", "international");

    private CfpMatcher() {
    }

    /**
     * Мероприятие с открытым CFP: из каталога или опубликованное организатором платформы (eventRequestId).
     * Необязательные поля null — «в источнике не указано», а не «нет требований».
     */
    public record CfpFact(String id, String title, String city, String level, String topic, LocalDate deadline,
                          String host, String url, List<String> tags,
                          String description, String talkFormat, String language, String audienceLevel,
                          String attendance, Integer talkDuration, String requirements, String eventRequestId) {

        public CfpFact(String id, String title, String city, String level, String topic, LocalDate deadline,
                       String host, String url, List<String> tags) {
            this(id, title, city, level, topic, deadline, host, url, tags, null, null, null, null, null, null, null, null);
        }

        public List<String> requirementItems() {
            if (requirements == null || requirements.isBlank()) {
                return List.of();
            }
            return Stream.of(requirements.split("[;\\n]"))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
    }

    public record Diagnostics(String cfpId, String group, double relevance, double confidence, Map<String, Double> factors) {
    }

    public record Outcome(List<String> notes, List<Group<EventRecommendationResult.Event>> groups, int considered,
                          List<Diagnostics> diagnostics) {
        public List<String> recommendedIds() {
            return groups.stream().flatMap(group -> group.items().stream()).map(EventRecommendationResult.Event::cfpId).toList();
        }
    }

    private record Scored(EventRecommendationResult.Event event, String group, double relevance, double confidence,
                          Map<String, Double> factors) {
    }

    public static Outcome match(ExpertFacts facts, ExpertProfile profile, List<CfpFact> cfps, LocalDate today, int maxEvents) {
        List<CfpFact> open = cfps.stream().filter(cfp -> cfp.deadline() != null && !cfp.deadline().isBefore(today)).toList();
        List<CfpFact> hidden = open.stream().filter(cfp -> markedIrrelevant(facts, cfp)).toList();
        open = open.stream().filter(cfp -> !hidden.contains(cfp)).toList();
        List<Scored> scored = new ArrayList<>();
        for (CfpFact cfp : open) {
            evaluate(facts, profile, cfp, today, scored);
        }
        Map<String, List<Scored>> byGroup = new LinkedHashMap<>();
        Stream.of(Recommendation.GROUP_HIGH, Recommendation.GROUP_POTENTIAL, Recommendation.GROUP_NEEDS_CHECK)
                .forEach(code -> byGroup.put(code, new ArrayList<>()));
        scored.stream()
                .sorted(Comparator.comparingDouble(Scored::relevance).reversed())
                .limit(maxEvents)
                .forEach(item -> byGroup.get(item.group()).add(item));
        List<Group<EventRecommendationResult.Event>> groups = new ArrayList<>();
        byGroup.forEach((code, items) -> {
            if (!items.isEmpty()) {
                groups.add(new Group<>(Recommendation.groupTitle(code), items.stream().map(Scored::event).toList(),
                        List.of(), code, Recommendation.groupDescription(code)));
            }
        });
        List<String> notes = new ArrayList<>();
        notes.add("Рассмотрено открытых CFP: " + open.size() + ". Мероприятия с прошедшим дедлайном не рассматриваются.");
        if (!hidden.isEmpty()) {
            notes.add("Не показаны мероприятия, которые вы отметили как неподходящие: " + hidden.size() + ".");
        }
        if (scored.isEmpty() && !open.isEmpty()) {
            notes.add("Ни одно открытое мероприятие не пересекается с вашей экспертизой — дополните профиль или теги докладов.");
        }
        List<Diagnostics> diagnostics = scored.stream()
                .map(item -> new Diagnostics(item.event().cfpId(), item.group(), round(item.relevance()), round(item.confidence()), item.factors()))
                .toList();
        return new Outcome(notes, groups, open.size(), diagnostics);
    }

    private static void evaluate(ExpertFacts facts, ExpertProfile profile, CfpFact cfp, LocalDate today, List<Scored> scored) {
        List<String> phrases = new ArrayList<>();
        phrases.add(cfp.topic());
        phrases.addAll(cfp.tags());
        Map<String, Term> unique = new LinkedHashMap<>();
        TermMatcher.terms(phrases).forEach(term -> unique.putIfAbsent(term.label().toLowerCase(Locale.ROOT), term));
        List<Term> terms = List.copyOf(unique.values());

        List<Coverage.Hit> hits = Coverage.compute(terms, facts, profile);
        double topic = Coverage.topicScore(hits, today);
        if (topic == 0) {
            return;
        }
        List<TalkFact> relevantTalks = Coverage.relevantTalks(hits);
        LocalDate lastTalk = relevantTalks.isEmpty() ? null : relevantTalks.get(0).date();

        List<String> why = new ArrayList<>();
        List<String> limitations = new ArrayList<>();
        List<String> unknowns = new ArrayList<>();

        List<String> confirmed = Coverage.labels(hits, ExpertProfile.CONFIRMED);
        if (!confirmed.isEmpty()) {
            TalkFact latest = relevantTalks.get(0);
            why.add("Тема пересекается с вашим подтверждённым опытом " + Texts.quoted(confirmed) + ": "
                    + Texts.talks(relevantTalks.size()) + ", последний — " + Texts.date(latest.date()) + " («" + latest.topic() + "»).");
        }
        List<String> claimed = Coverage.labels(hits, ExpertProfile.POTENTIAL);
        if (!claimed.isEmpty()) {
            limitations.add("Опыт по " + Texts.quoted(claimed) + " заявлен, но не подтверждён выступлениями.");
        }
        List<String> adjacent = Coverage.labels(hits, ExpertProfile.ADJACENT);
        if (!adjacent.isEmpty()) {
            limitations.add("По " + Texts.quoted(adjacent) + " у вас только смежный опыт.");
        }
        if (lastTalk != null && Coverage.daysSince(lastTalk, today) > 365) {
            limitations.add("Последний доклад по теме — " + Texts.date(lastTalk) + ": опыт менее актуален.");
        }

        int stage = stageIndex(facts.stage());
        int level = stageIndex(cfp.level());
        int diff = level - stage;
        double levelFit;
        String levelName = Texts.stage(cfp.level());
        if (diff == 0) {
            levelFit = 1.0;
            why.add("Уровень мероприятия (" + levelName + ") соответствует вашему текущему.");
        } else if (diff == 1) {
            levelFit = 0.9;
            why.add("Уровень мероприятия (" + levelName + ") — следующая ступень после вашей текущей ("
                    + Texts.stage(facts.stage()) + "): хорошая возможность для роста.");
        } else if (diff >= 2) {
            levelFit = 0.3;
            limitations.add("Уровень мероприятия (" + levelName + ") на " + diff + " ступени выше вашего текущего ("
                    + Texts.stage(facts.stage()) + ") — заявка рискованная.");
        } else {
            levelFit = diff == -1 ? 0.6 : 0.5;
            limitations.add("Уровень мероприятия (" + levelName + ") ниже вашего текущего (" + Texts.stage(facts.stage()) + ").");
        }

        boolean geoCritical = false;
        boolean travelUnknown = false;
        double geo;
        String attendance = cfp.attendance() == null ? null : cfp.attendance().toLowerCase(Locale.ROOT);
        if ("online".equals(attendance)) {
            geo = 1.0;
            why.add("Выступление онлайн — город не важен.");
        } else if (SpeakerMatcher.sameCity(facts.region(), cfp.city())) {
            geo = 1.0;
            why.add("Проходит в вашем городе: " + cfp.city() + ".");
        } else if ("hybrid".equals(attendance)) {
            geo = Boolean.FALSE.equals(facts.preferences().onlineOk()) ? 0.5 : 0.9;
            why.add("Гибридный формат: можно выступить онлайн, не приезжая в город " + cfp.city() + ".");
        } else if (Boolean.FALSE.equals(facts.preferences().offlineOk())) {
            geo = 0.2;
            geoCritical = "offline".equals(attendance);
            limitations.add("Проходит в городе " + cfp.city() + ", а в профиле указано, что вы не выступаете офлайн.");
        } else {
            geo = 0.5;
            travelUnknown = facts.preferences().offlineOk() == null;
            unknowns.add("Город проведения — " + cfp.city() + "; "
                    + (attendance == null ? "формат участия в данных CFP не указан" : "участие только очно")
                    + (travelUnknown ? ", готовность к поездкам в профиле не указана." : "."));
        }

        double interests = 0.5;
        List<String> matchedInterests = facts.preferences().interests().stream()
                .filter(interest -> terms.stream().anyMatch(term -> TermMatcher.matches(term, TermMatcher.keys(interest))))
                .toList();
        if (!matchedInterests.isEmpty()) {
            interests = 1.0;
            why.add("Совпадает с вашими интересами: " + Texts.quoted(matchedInterests) + ".");
        }

        long daysLeft = ChronoUnit.DAYS.between(today, cfp.deadline());
        if (daysLeft <= 7) {
            limitations.add("До дедлайна подачи осталось " + Texts.plural(daysLeft, "день", "дня", "дней") + ".");
        }
        ExpertFacts.CfpHistory history = facts.signals().cfpHistory().get(cfp.id());
        if (history != null) {
            limitations.add(historyNote(history));
        }

        int missingFields = 0;
        boolean languageCritical = false;
        if (cfp.language() == null || cfp.language().isBlank()) {
            missingFields++;
            unknowns.add("Язык мероприятия в данных CFP не указан.");
        } else if (facts.preferences().languages().isEmpty()) {
            unknowns.add("Мероприятие на языке «" + cfp.language() + "», а языки выступлений в профиле не указаны.");
        } else if (facts.preferences().languages().stream().anyMatch(item -> sameLanguage(item, cfp.language()))) {
            why.add("Язык мероприятия совпадает с вашим: " + cfp.language() + ".");
        } else {
            languageCritical = true;
            limitations.add("Мероприятие на языке «" + cfp.language() + "», а в профиле указаны " + Texts.quoted(facts.preferences().languages()) + ".");
        }

        String talkFormat = describeFormat(cfp);
        if (talkFormat == null) {
            missingFields++;
            unknowns.add("Формат и длительность выступления не указаны.");
        } else if (cfp.talkFormat() != null && !facts.preferences().formats().isEmpty()) {
            boolean preferred = facts.preferences().formats().stream().anyMatch(format -> sameFormat(format, cfp.talkFormat()));
            if (preferred) {
                why.add("Формат «" + cfp.talkFormat() + "» есть среди ваших предпочтений.");
            } else {
                limitations.add("Формат «" + cfp.talkFormat() + "» не входит в ваши предпочтения " + Texts.quoted(facts.preferences().formats()) + ".");
            }
        }

        List<String> requirementItems = cfp.requirementItems();
        if (requirementItems.isEmpty()) {
            missingFields++;
            unknowns.add("Требования к спикеру и к заявке в источнике не указаны.");
        }
        for (String requirement : requirementItems) {
            Coverage.Hit hit = Coverage.compute(List.of(TermMatcher.term(requirement)), facts, profile).get(0);
            String coverage = hit.level();
            if (ExpertProfile.CONFIRMED.equals(coverage)) {
                why.add("Требование «" + requirement + "» подтверждено вашими докладами: " + hit.talks().size() + ".");
            } else if (ExpertProfile.POTENTIAL.equals(coverage)) {
                limitations.add("Требование «" + requirement + "» заявлено (" + hit.claimedSource() + "), но не подтверждено докладами.");
            } else {
                unknowns.add("Требование «" + requirement + "» по данным платформы проверить нельзя.");
            }
        }

        double relevance = 0.45 * topic + 0.12 * Coverage.depth(relevantTalks.size()) + 0.10 * Coverage.recency(lastTalk, today)
                + 0.18 * levelFit + 0.10 * geo + 0.05 * interests;

        List<String> reasons = new ArrayList<>();
        double confidence = 1.0;
        if (relevantTalks.isEmpty()) {
            confidence -= 0.3;
            reasons.add("Ваш опыт по теме не подтверждён выступлениями.");
        } else if (Coverage.daysSince(lastTalk, today) > 730) {
            confidence -= 0.15;
            reasons.add("Последнее выступление по теме — больше двух лет назад.");
        }
        if (missingFields >= 2) {
            confidence -= 0.1;
            reasons.add("В данных CFP мало сведений: не указаны язык, формат или требования к спикеру.");
        } else if (missingFields == 1) {
            confidence -= 0.05;
            reasons.add("В данных CFP указано не всё — см. неизвестные параметры.");
        }
        long gaps = profile.gaps().stream().filter(gap -> List.of("about", "companyRole", "languages").contains(gap.field())).count();
        if (gaps > 0) {
            confidence -= Math.min(0.15, 0.05 * gaps);
            reasons.add("Профиль заполнен не полностью — см. пробелы в профиле.");
        }
        if (travelUnknown) {
            confidence -= 0.05;
            reasons.add("Готовность выступать в другом городе не указана.");
        }
        confidence = Math.max(0, confidence);
        if (reasons.isEmpty()) {
            reasons.add("Опыт по теме подтверждён свежими выступлениями, данных CFP достаточно для проверки.");
        }
        String confidenceLevel = Recommendation.confidenceLevel(confidence);

        // Критическое несоответствие требованиям CFP не даёт уверенной рекомендации (US-12).
        boolean critical = diff >= 2 || geoCritical || languageCritical;
        String group;
        if (Recommendation.CONFIDENCE_LOW.equals(confidenceLevel)) {
            group = Recommendation.GROUP_NEEDS_CHECK;
        } else if (relevance >= SpeakerMatcher.HIGH_RELEVANCE && !relevantTalks.isEmpty() && !critical) {
            group = Recommendation.GROUP_HIGH;
        } else {
            group = relevance >= SpeakerMatcher.POTENTIAL_RELEVANCE ? Recommendation.GROUP_POTENTIAL : Recommendation.GROUP_NEEDS_CHECK;
        }

        List<String> requirements = new ArrayList<>();
        requirements.add("Тема CFP: " + cfp.topic() + ".");
        if (!cfp.tags().isEmpty()) {
            requirements.add("Направления: " + String.join(", ", cfp.tags()) + ".");
        }
        requirements.add("Уровень мероприятия: " + levelName + ".");
        requirements.add("Город: " + cfp.city() + (attendance == null ? "" : ", участие: " + Texts.format(attendance)) + ".");
        if (cfp.language() != null && !cfp.language().isBlank()) {
            requirements.add("Язык: " + cfp.language() + ".");
        }
        if (talkFormat != null) {
            requirements.add("Выступление: " + talkFormat + ".");
        }
        if (cfp.audienceLevel() != null && !cfp.audienceLevel().isBlank()) {
            requirements.add("Уровень аудитории: " + EventParamKey.AUDIENCE_LEVEL.display(List.of(cfp.audienceLevel())) + ".");
        }
        if (!requirementItems.isEmpty()) {
            requirements.add("Требования к спикеру: " + String.join("; ", requirementItems) + ".");
        }
        requirements.add("Дедлайн подачи: " + Texts.date(cfp.deadline()) + ".");

        String description = cfp.description() == null || cfp.description().isBlank()
                ? "Тема: " + cfp.topic() + ". Организатор: " + cfp.host() + ". Город: " + cfp.city() + ". Уровень: " + levelName + "."
                : cfp.description().trim();
        EventRecommendationResult.Event event = new EventRecommendationResult.Event(
                cfp.title(),
                null,
                why,
                new EventRecommendationResult.Cfp(true, cfp.deadline(), daysLeft, cfp.url()),
                limitations,
                unknowns,
                new Recommendation.Confidence(confidenceLevel, reasons),
                requirements,
                Coverage.talkRefs(relevantTalks, hits, 3),
                description,
                cfp.id(),
                cfp.eventRequestId()
        );
        Map<String, Double> factors = new LinkedHashMap<>();
        factors.put("topic", round(topic));
        factors.put("depth", Coverage.depth(relevantTalks.size()));
        factors.put("recency", Coverage.recency(lastTalk, today));
        factors.put("level", levelFit);
        factors.put("geo", geo);
        factors.put("interests", interests);
        scored.add(new Scored(event, group, relevance, confidence, factors));
    }

    private static boolean markedIrrelevant(ExpertFacts facts, CfpFact cfp) {
        ExpertFacts.CfpHistory history = facts.signals().cfpHistory().get(cfp.id());
        return history != null && "marked_irrelevant".equals(history.action());
    }

    private static String historyNote(ExpertFacts.CfpHistory history) {
        return switch (history.action()) {
            case "application_rejected" -> "Ваша прошлая заявка на этот CFP (" + Texts.date(history.date())
                    + ") отклонена — это не означает отсутствие экспертизы.";
            case "application_accepted" -> "Ваша заявка на этот CFP уже принята (" + Texts.date(history.date()) + ").";
            case "application_reserve" -> "Ваша заявка на этот CFP в резерве (" + Texts.date(history.date()) + ").";
            default -> "По этому CFP уже есть ваше действие от " + Texts.date(history.date()) + " — заявка подана или открыта.";
        };
    }

    private static String describeFormat(CfpFact cfp) {
        boolean format = cfp.talkFormat() != null && !cfp.talkFormat().isBlank();
        boolean duration = cfp.talkDuration() != null && cfp.talkDuration() > 0;
        if (!format && !duration) {
            return null;
        }
        if (format && duration) {
            return cfp.talkFormat().trim() + ", " + cfp.talkDuration() + " минут";
        }
        return format ? cfp.talkFormat().trim() : cfp.talkDuration() + " минут";
    }

    static boolean sameLanguage(String a, String b) {
        String left = TermMatcher.normalize(a);
        String right = TermMatcher.normalize(b);
        int prefix = Math.min(3, Math.min(left.length(), right.length()));
        return prefix > 0 && left.substring(0, prefix).equals(right.substring(0, prefix));
    }

    /** «доклад» подходит к «доклад или лайвкодинг»: формат CFP может перечислять несколько вариантов. */
    static boolean sameFormat(String preferred, String required) {
        String left = TermMatcher.normalize(preferred);
        String right = TermMatcher.normalize(required);
        return !left.isEmpty() && (right.contains(left) || left.contains(right));
    }

    private static int stageIndex(String stage) {
        int index = STAGES.indexOf(stage == null ? "" : stage.toLowerCase(Locale.ROOT));
        return index < 0 ? 0 : index;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
