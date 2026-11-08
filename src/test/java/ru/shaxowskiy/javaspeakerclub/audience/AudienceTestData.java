package ru.shaxowskiy.javaspeakerclub.audience;

import ru.shaxowskiy.javaspeakerclub.audience.material.Fragment;
import ru.shaxowskiy.javaspeakerclub.audience.material.MaterialSnapshot;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfile;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfileDraft;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.EventsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerTalksRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AudienceTestData {

    private AudienceTestData() {
    }

    public static AudienceProperties properties() {
        return properties(30000, 1500, 200);
    }

    public static AudienceProperties properties(int maxMaterialChars, int maxFragmentChars, int minMaterialChars) {
        return new AudienceProperties(true, "event-audience-v3", "audience-fit-v3", 2500,
                maxMaterialChars, maxFragmentChars, minMaterialChars, 200, 20, 30, 70, 45, 10);
    }

    public static EventsRecord event(UUID id, Long ownerId, String state) {
        EventsRecord event = new EventsRecord();
        event.setId(id);
        event.setOwnerId(ownerId);
        event.setName("JPoint 2026");
        event.setEventDate(LocalDate.now().plusDays(30));
        event.setCity("Москва");
        event.setFormat("OFFLINE");
        event.setTopic("Java");
        event.setAudience("Senior Java-разработчики");
        event.setDescription("Хардкорная конференция для опытных Java-разработчиков");
        event.setState(state);
        return event;
    }

    public static SpeakerTalksRecord talk(UUID id, Long speakerId, String description) {
        SpeakerTalksRecord talk = new SpeakerTalksRecord();
        talk.setId(id);
        talk.setSpeakerId(speakerId);
        talk.setName("Virtual threads в продакшене");
        talk.setTopic("Java");
        talk.setDescription(description);
        return talk;
    }

    public static MaterialSnapshot material(Fragment... fragments) {
        int total = 0;
        for (Fragment fragment : fragments) {
            total += fragment.text().length();
        }
        return new MaterialSnapshot("Доклад", "slides.pptx", List.of(fragments), total, false, null);
    }

    /** Профиль с преимущественной ЦА segment и ожиданием по наглядности visual. */
    public static EventAudienceProfile profile(String segment, String label, String visual, String confidence) {
        return new EventAudienceProfile("Кто придёт", segment, label, "Так сказано в описании", Map.of(segment, 100), null, visual,
                Map.of("senior", 80, "middle", 20), List.of("backend"), List.of("Java"), List.of("кейсы"), List.of("Spring"),
                List.of("основы"), confidence, List.of());
    }

    public static EventAudienceProfileDraft draft(String segment, Map<String, Object> experience, EventAudienceProfileDraft.Age age,
                                                  String confidence, List<EventAudienceProfileDraft.Evidence> evidence) {
        return new EventAudienceProfileDraft("Опытные разработчики", segment, "Senior Java-разработчики", "В описании: хардкор",
                experience, age, null, Map.of("senior", 70, "middle", 30), List.of("backend"), List.of("Java"), List.of("кейсы"),
                List.of("Spring"), List.of("реклама"), confidence, evidence);
    }
}
