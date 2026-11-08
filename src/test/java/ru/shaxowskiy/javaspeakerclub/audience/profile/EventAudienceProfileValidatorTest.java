package ru.shaxowskiy.javaspeakerclub.audience.profile;

import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.audience.Rejection;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.AcceptedTalk;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.EventInfo;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.PastEdition;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventAudienceProfileValidatorTest {

    private final EventAudienceProfileValidator validator = new EventAudienceProfileValidator();

    @Test
    void normalizesLevelsToHundredPercent() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("junior", 1);
        raw.put("middle", "1");
        raw.put("senior", "1%");

        Map<String, Integer> levels = EventAudienceProfileValidator.normalizeLevels(raw, new ArrayList<>());

        assertThat(levels).containsOnlyKeys("junior", "middle", "senior", "lead");
        assertThat(levels.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
        assertThat(levels.get("lead")).isZero();
        assertThat(levels.get("junior")).isBetween(33, 34);
    }

    @Test
    void dropsUnknownLevelsAndMapsTeamLead() {
        List<Rejection> rejections = new ArrayList<>();
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("teamlead", 20);
        raw.put("senior", 80);
        raw.put("guru", 50);

        Map<String, Integer> levels = EventAudienceProfileValidator.normalizeLevels(raw, rejections);

        assertThat(levels).containsEntry("lead", 20).containsEntry("senior", 80);
        assertThat(rejections).extracting(Rejection::reason).containsExactly("Неизвестный уровень");
    }

    @Test
    void emptyLevelsWhenNothingUsable() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("junior", "много");
        raw.put("middle", -5);

        assertThat(EventAudienceProfileValidator.normalizeLevels(raw, new ArrayList<>())).isEmpty();
        assertThat(EventAudienceProfileValidator.normalizeLevels(null, new ArrayList<>())).isEmpty();
    }

    @Test
    void keepsOnlyEvidenceThatExistsInInputs() {
        EventAudienceInputs inputs = inputs("Senior-разработчики", null, 2);
        EventAudienceProfileDraft draft = draft("high", List.of(
                new EventAudienceProfileDraft.Evidence("EVENT", "audience"),
                new EventAudienceProfileDraft.Evidence("EVENT", "description"),
                new EventAudienceProfileDraft.Evidence("ACCEPTED_TALK", "talk-1"),
                new EventAudienceProfileDraft.Evidence("ACCEPTED_TALK", "talk-404"),
                new EventAudienceProfileDraft.Evidence("PAST_EDITION", "edition-1"),
                new EventAudienceProfileDraft.Evidence("WEB", "x")
        ));

        EventAudienceProfileValidator.Result result = validator.validate(draft, inputs);

        assertThat(result.profile().evidence())
                .extracting(EventAudienceProfile.Evidence::source, EventAudienceProfile.Evidence::id)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("EVENT", "audience"),
                        org.assertj.core.groups.Tuple.tuple("ACCEPTED_TALK", "talk-1"),
                        org.assertj.core.groups.Tuple.tuple("PAST_EDITION", "edition-1")
                );
        assertThat(result.profile().evidence().get(1).label()).isEqualTo("Доклад программы «Доклад 1»");
        // description пустое, talk-404 и WEB нет во входных данных
        assertThat(result.rejections()).hasSize(3);
    }

    @Test
    void confidenceIsCappedByAvailableData() {
        EventAudienceInputs thin = inputs(null, null, 0);

        EventAudienceProfileValidator.Result result = validator.validate(draft("high", List.of()), thin);

        assertThat(result.profile().confidence()).isEqualTo("low");
        assertThat(result.rejections()).extracting(Rejection::item).contains("Уверенность");
    }

    @Test
    void confidenceCapLevels() {
        assertThat(EventAudienceProfileValidator.confidenceCap(inputs("Senior", null, 2))).isEqualTo("high");
        assertThat(EventAudienceProfileValidator.confidenceCap(inputs("Senior", null, 0))).isEqualTo("medium");
        assertThat(EventAudienceProfileValidator.confidenceCap(inputs(null, null, 1))).isEqualTo("medium");
        assertThat(EventAudienceProfileValidator.confidenceCap(inputs(null, "x".repeat(200), 0))).isEqualTo("medium");
        assertThat(EventAudienceProfileValidator.confidenceCap(inputs(null, "коротко", 0))).isEqualTo("low");
    }

    @Test
    void missingLevelsLowerConfidence() {
        EventAudienceProfileDraft draft = new EventAudienceProfileDraft("Кто придёт", Map.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), "medium", List.of());

        EventAudienceProfileValidator.Result result = validator.validate(draft, inputs("Senior", null, 2));

        assertThat(result.profile().confidence()).isEqualTo("low");
        assertThat(result.profile().levels()).isEmpty();
    }

    @Test
    void cleansListsAndSummary() {
        EventAudienceProfileDraft draft = new EventAudienceProfileDraft("  " + "к".repeat(700) + " ", Map.of("senior", 100),
                List.of(" backend ", "Backend", "", "QA", "a", "b", "c", "d", "e"), null, null, null, null, "bogus", null);

        EventAudienceProfile profile = validator.validate(draft, inputs("Senior", null, 2)).profile();

        assertThat(profile.roles()).hasSize(6).first().isEqualTo("backend");
        assertThat(profile.roles()).doesNotContain("Backend", "");
        assertThat(profile.domains()).isEmpty();
        assertThat(profile.summary()).hasSize(600).endsWith("…");
        assertThat(profile.confidence()).isEqualTo("high");
    }

    @Test
    void manualProfileComesFromDevrel() {
        EventAudienceProfile profile = validator.manual(new EventAudienceProfileUpdate(
                "Тимлиды", Map.of("lead", 3, "senior", 1), List.of("тимлиды"), null, null, null, null));

        assertThat(profile.levels()).containsEntry("lead", 75).containsEntry("senior", 25);
        assertThat(profile.confidence()).isEqualTo("high");
        assertThat(profile.evidence()).extracting(EventAudienceProfile.Evidence::source).containsExactly("DEVREL");
    }

    private static EventAudienceProfileDraft draft(String confidence, List<EventAudienceProfileDraft.Evidence> evidence) {
        return new EventAudienceProfileDraft("Опытные разработчики", Map.of("senior", 70, "middle", 30),
                List.of("backend"), List.of("Java"), List.of("кейсы"), List.of("Spring"), List.of("реклама"),
                confidence, evidence);
    }

    private static EventAudienceInputs inputs(String audience, String description, int acceptedTalks) {
        List<AcceptedTalk> talks = new ArrayList<>();
        for (int i = 1; i <= acceptedTalks; i++) {
            talks.add(new AcceptedTalk("talk-" + i, "Доклад " + i, "Java", null));
        }
        return new EventAudienceInputs(
                new EventInfo("JPoint 2026", LocalDate.of(2026, 12, 1), "Москва", "OFFLINE", "Java", audience, description),
                talks,
                List.of(new PastEdition("edition-1", "JPoint", "Иван Петров", "Яндекс", LocalDate.of(2025, 4, 1)))
        );
    }
}
