package ru.shaxowskiy.javaspeakerclub.producer.matching;

import ru.shaxowskiy.javaspeakerclub.producer.matching.analysis.EventCardAssembler;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventAnalysisDraft;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventCard;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.EventState;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.FeedbackFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.Preferences;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.Signals;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertFacts.TalkFact;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.ExpertProfile;
import ru.shaxowskiy.javaspeakerclub.producer.matching.profile.ExpertProfileBuilder;
import ru.shaxowskiy.javaspeakerclub.producer.matching.profile.TagCooccurrence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Факты и карточки для модульных тестов подбора: без базы и без модели. */
public final class MatchingFixtures {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 16);
    public static final UUID EVENT_ID = UUID.fromString("00000000-0000-4000-8000-0000000000e1");

    private MatchingFixtures() {
    }

    public static TalkFact talk(String id, String topic, int daysAgo, String type, String format, String... tags) {
        return new TalkFact(id, topic, TODAY.minusDays(daysAgo), "Conf " + id, type, format, List.of(tags));
    }

    public static FeedbackFact feedback(String id, String talkId, int rating, String authorType, String positive, String negative) {
        return new FeedbackFact(id, talkId, rating, positive, negative, "-", authorType, TODAY.minusDays(5));
    }

    public static Preferences preferences(List<String> languages, Boolean online, Boolean offline, String... interests) {
        return new Preferences(languages, List.of("доклад"), online, offline, List.of(interests), null);
    }

    public static ExpertFacts expert(long id, String name, String region, String role, String about, List<String> profileTags,
                                     Preferences preferences, List<TalkFact> talks, List<FeedbackFact> feedback) {
        return new ExpertFacts(id, "user" + id, name, "Компания", role, region, "regional", about, profileTags, preferences,
                talks, feedback, Signals.empty());
    }

    public static ExpertProfile profile(ExpertFacts facts, List<String> vocabulary) {
        return ExpertProfileBuilder.build(facts, TagCooccurrence.empty(), vocabulary, TODAY);
    }

    public static Map<Long, ExpertProfile> profiles(List<ExpertFacts> experts, List<String> vocabulary) {
        Map<Long, ExpertProfile> profiles = new LinkedHashMap<>();
        experts.forEach(facts -> profiles.put(facts.id(), profile(facts, vocabulary)));
        return profiles;
    }

    /** Карточка из полей формы и требований организатора, без разбора описания. */
    public static EventCard card(Map<String, List<String>> params, List<EventState.CriterionInput> criteria) {
        EventState state = new EventState(new LinkedHashMap<>(params), new LinkedHashMap<>(), new LinkedHashSet<>(), criteria,
                new LinkedHashSet<>(), null, List.of(), null, null);
        return EventCardAssembler.assemble(EVENT_ID, "Mobile Day", null, EventCard.STATUS_ANALYZED, state, List.of(), 4,
                LocalDateTime.of(2026, 10, 16, 12, 0));
    }

    public static EventState stateWithDraft(EventAnalysisDraft draft) {
        return new EventState(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashSet<>(), List.of(), new LinkedHashSet<>(),
                draft, List.of(), null, null);
    }
}
