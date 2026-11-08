package ru.shaxowskiy.javaspeakerclub.audience.profile;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import ru.shaxowskiy.javaspeakerclub.audience.AudienceTestData;
import ru.shaxowskiy.javaspeakerclub.audience.Rejection;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.AcceptedTalk;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.EventInfo;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.PastEdition;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfileDraft.Age;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EventAudienceProfileValidatorTest {

    private final EventAudienceProfileValidator validator = new EventAudienceProfileValidator();

    // ---------- Преимущественная ЦА ----------

    @Test
    void primarySegmentIsTheLargestShare() {
        EventAudienceProfileDraft draft = AudienceTestData.draft("practitioners",
                Map.of("students", 70, "beginners", 20, "practitioners", 10), null, "medium", List.of());

        EventAudienceProfileValidator.Result result = validator.validate(draft, inputs("Студенты 2–4 курсов", null, 0));

        assertThat(result.profile().primarySegment()).isEqualTo("students");
        assertThat(result.profile().experience()).containsEntry("students", 70).containsEntry("experts", 0);
        // подпись модели относилась к другому сегменту — берётся стандартная
        assertThat(result.profile().segmentLabel()).isEqualTo("Студенты");
        assertThat(result.rejections()).extracting(Rejection::reason).anyMatch(reason -> reason.contains("наибольшая доля"));
    }

    @Test
    void keepsModelLabelWhenSegmentAgrees() {
        EventAudienceProfileDraft draft = AudienceTestData.draft("experts", Map.of("experts", 60, "practitioners", 40), null, "high", List.of());

        EventAudienceProfile profile = validator.validate(draft, inputs("Архитекторы", null, 2)).profile();

        assertThat(profile.primarySegment()).isEqualTo("experts");
        assertThat(profile.segmentLabel()).isEqualTo("Senior Java-разработчики");
        assertThat(profile.segmentReason()).isEqualTo("В описании: хардкор");
        assertThat(profile.visualExpectation()).isEqualTo("low");
    }

    @Test
    void tieGoesToModelChoice() {
        assertThat(EventAudienceProfileValidator.primary(Map.of("students", 50, "beginners", 50), "beginners")).isEqualTo("beginners");
        assertThat(EventAudienceProfileValidator.primary(Map.of("students", 50, "beginners", 50), null)).isEqualTo("students");
        assertThat(EventAudienceProfileValidator.primary(Map.of(), "students")).isNull();
    }

    @Test
    void segmentFromModelOrLevelsWhenSharesMissing() {
        EventAudienceProfile onlyPrimary = validator.validate(
                AudienceTestData.draft("Student", null, null, "medium", List.of()), inputs("Студенты", null, 0)).profile();
        assertThat(onlyPrimary.primarySegment()).isEqualTo("students");
        assertThat(onlyPrimary.experience()).containsEntry("students", 100);
        assertThat(onlyPrimary.visualExpectation()).isEqualTo("high");

        EventAudienceProfile fromLevels = validator.validate(
                AudienceTestData.draft(null, null, null, "medium", List.of()), inputs("Бэкенд", null, 0)).profile();
        // senior 70 + middle 30 → практики
        assertThat(fromLevels.primarySegment()).isEqualTo("practitioners");
        assertThat(fromLevels.experience()).containsEntry("practitioners", 100);
    }

    @Test
    void experienceFromLevelsMapping() {
        assertThat(EventAudienceProfileValidator.experienceFromLevels(Map.of("junior", 60, "middle", 20, "senior", 10, "lead", 10)))
                .containsEntry("beginners", 60).containsEntry("practitioners", 30).containsEntry("experts", 10);
    }

    @Test
    void noSegmentMeansLowConfidence() {
        EventAudienceProfileDraft draft = new EventAudienceProfileDraft("?", null, null, null, null, null, null, Map.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), "high", List.of());

        EventAudienceProfile profile = validator.validate(draft, inputs("Senior", null, 2)).profile();

        assertThat(profile.primarySegment()).isNull();
        assertThat(profile.confidence()).isEqualTo("low");
        assertThat(profile.visualExpectation()).isEqualTo("medium");
    }

    // ---------- Возраст ----------

    @Test
    void ageAcceptedOnlyWhenStatedInEventField() {
        EventAudienceInputs students = inputs("Студенты 2–4 курсов", null, 0);

        EventAudienceProfile accepted = validator.validate(
                AudienceTestData.draft("students", Map.of("students", 100), new Age(23, "18", "audience"), "medium", List.of()), students).profile();
        assertThat(accepted.ageRange()).isEqualTo(new EventAudienceProfile.AgeRange(18, 23));

        EventAudienceProfileValidator.Result guessed = validator.validate(
                AudienceTestData.draft("practitioners", Map.of("practitioners", 100), new Age(25, 40, "audience"), "medium", List.of()),
                inputs("Бэкенд-разработчики", null, 0));
        assertThat(guessed.profile().ageRange()).isNull();
        assertThat(guessed.rejections()).extracting(Rejection::item).contains("Возраст");

        EventAudienceProfile wrongField = validator.validate(
                AudienceTestData.draft("students", Map.of("students", 100), new Age(18, 23, "format"), "medium", List.of()), students).profile();
        assertThat(wrongField.ageRange()).isNull();
    }

    @Test
    void ageMarkersAndRange() {
        EventAudienceInputs teens = inputs("IT для подростков 14+", null, 0);
        EventAudienceProfile profile = validator.validate(
                AudienceTestData.draft("schoolchildren", Map.of("schoolchildren", 100), new Age(14, null, "audience"), "medium", List.of()), teens).profile();
        assertThat(profile.ageRange()).isEqualTo(new EventAudienceProfile.AgeRange(14, null));

        assertThat(EventAudienceProfileValidator.ageRange(3, 10)).isNull();
        assertThat(EventAudienceProfileValidator.ageRange(30, 120)).isNull();
        assertThat(EventAudienceProfileValidator.ageRange(null, null)).isNull();
    }

    // ---------- Наглядность ----------

    @Test
    void visualExpectationDefaultsBySegment() {
        assertThat(EventAudienceProfileValidator.visual(null, "schoolchildren")).isEqualTo("high");
        assertThat(EventAudienceProfileValidator.visual(null, "beginners")).isEqualTo("high");
        assertThat(EventAudienceProfileValidator.visual("bogus", "practitioners")).isEqualTo("medium");
        assertThat(EventAudienceProfileValidator.visual(null, "experts")).isEqualTo("low");
        assertThat(EventAudienceProfileValidator.visual(" HIGH ", "experts")).isEqualTo("high");
    }

    @Test
    void missingConfidenceFallsBackToCap() {
        EventAudienceProfile profile = validator.validate(
                AudienceTestData.draft("practitioners", Map.of("practitioners", 100), null, null, List.of()), inputs("Senior", null, 0)).profile();

        assertThat(profile.confidence()).isEqualTo("medium");
    }

    // ---------- Доли, источники, уверенность ----------

    @Test
    void normalizesSharesToHundredPercent() {
        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("junior", 1);
        raw.put("middle", "1");
        raw.put("senior", "1%");
        raw.put("guru", 50);
        List<Rejection> rejections = new ArrayList<>();

        Map<String, Integer> levels = EventAudienceProfileValidator.normalizeLevels(raw, rejections);

        assertThat(levels.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(100);
        assertThat(levels.get("lead")).isZero();
        assertThat(rejections).extracting(Rejection::reason).containsExactly("Неизвестное значение");
        assertThat(EventAudienceProfileValidator.normalizeLevels(Map.of("junior", "много"), new ArrayList<>())).isEmpty();
    }

    @Test
    void keepsOnlyEvidenceThatExistsInInputs() {
        EventAudienceProfileDraft draft = AudienceTestData.draft("practitioners", Map.of("practitioners", 100), null, "high", List.of(
                new EventAudienceProfileDraft.Evidence("EVENT", "audience"),
                new EventAudienceProfileDraft.Evidence("EVENT", "description"),
                new EventAudienceProfileDraft.Evidence("ACCEPTED_TALK", "talk-1"),
                new EventAudienceProfileDraft.Evidence("ACCEPTED_TALK", "talk-404"),
                new EventAudienceProfileDraft.Evidence("PAST_EDITION", "edition-1")
        ));

        EventAudienceProfile profile = validator.validate(draft, inputs("Senior-разработчики", null, 2)).profile();

        assertThat(profile.evidence())
                .extracting(EventAudienceProfile.Evidence::source, EventAudienceProfile.Evidence::id)
                .containsExactly(Tuple.tuple("EVENT", "audience"), Tuple.tuple("ACCEPTED_TALK", "talk-1"), Tuple.tuple("PAST_EDITION", "edition-1"));
    }

    @Test
    void confidenceIsCappedByAvailableData() {
        assertThat(validator.validate(AudienceTestData.draft("practitioners", Map.of("practitioners", 100), null, "high", List.of()),
                inputs(null, null, 0)).profile().confidence()).isEqualTo("low");
        assertThat(EventAudienceProfileValidator.confidenceCap(inputs("Senior", null, 2))).isEqualTo("high");
        assertThat(EventAudienceProfileValidator.confidenceCap(inputs(null, "x".repeat(200), 0))).isEqualTo("medium");
    }

    @Test
    void manualProfileUsesChosenSegment() {
        EventAudienceProfile profile = validator.manual(new EventAudienceProfileUpdate(
                "Студенты", "students", null, 23, 18, null, Map.of("junior", 3, "middle", 1), List.of("студенты"), null, null, null, null));

        assertThat(profile.primarySegment()).isEqualTo("students");
        assertThat(profile.segmentLabel()).isEqualTo("Студенты");
        assertThat(profile.experience()).containsEntry("students", 100);
        assertThat(profile.ageRange()).isEqualTo(new EventAudienceProfile.AgeRange(18, 23));
        assertThat(profile.visualExpectation()).isEqualTo("high");
        assertThat(profile.levels()).containsEntry("junior", 75);
        assertThat(profile.evidence()).extracting(EventAudienceProfile.Evidence::source).containsExactly("DEVREL");
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
