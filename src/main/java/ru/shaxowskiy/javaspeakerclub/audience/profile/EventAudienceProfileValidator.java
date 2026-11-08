package ru.shaxowskiy.javaspeakerclub.audience.profile;

import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.audience.Rejection;
import ru.shaxowskiy.javaspeakerclub.audience.Texts;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.AcceptedTalk;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.EventInfo;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceInputs.PastEdition;
import ru.shaxowskiy.javaspeakerclub.audience.profile.EventAudienceProfile.Evidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Пропускает в профиль только то, что опирается на входные данные: ссылки на источники проверяются,
 * доли уровней приводятся к 100%, а уверенность не может быть выше, чем позволяют данные.
 */
@Component
public class EventAudienceProfileValidator {

    public static final List<String> LEVELS = List.of("junior", "middle", "senior", "lead");
    public static final List<String> CONFIDENCE = List.of("low", "medium", "high");

    private static final Map<String, String> EVENT_FIELDS = Map.of(
            "name", "Название мероприятия",
            "topic", "Тема мероприятия",
            "audience", "Заявленная аудитория",
            "description", "Описание мероприятия",
            "format", "Формат мероприятия"
    );
    private static final int MAX_ITEMS = 6;
    private static final int MAX_ITEM_LENGTH = 120;
    private static final int MAX_SUMMARY_LENGTH = 600;
    private static final int MAX_EVIDENCE = 12;
    private static final int RICH_DESCRIPTION_CHARS = 150;

    public record Result(EventAudienceProfile profile, List<Rejection> rejections) {
    }

    public Result validate(EventAudienceProfileDraft draft, EventAudienceInputs inputs) {
        List<Rejection> rejections = new ArrayList<>();
        Map<String, Integer> levels = normalizeLevels(draft.levels(), rejections);
        List<Evidence> evidence = validateEvidence(draft.evidence(), inputs, rejections);

        String cap = confidenceCap(inputs);
        String confidence = normalizeConfidence(draft.confidence());
        if (confidence == null) {
            confidence = cap;
        } else if (CONFIDENCE.indexOf(confidence) > CONFIDENCE.indexOf(cap)) {
            rejections.add(new Rejection("Уверенность", "Снижена с " + confidence + " до " + cap + ": данных о мероприятии мало"));
            confidence = cap;
        }
        if (levels.isEmpty() && !"low".equals(confidence)) {
            rejections.add(new Rejection("Уверенность", "Снижена до low: модель не определила уровень аудитории"));
            confidence = "low";
        }

        EventAudienceProfile profile = new EventAudienceProfile(
                summary(draft.summary()),
                levels,
                Texts.cleanList(draft.roles(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(draft.domains(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(draft.expectations(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(draft.assumedKnowledge(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(draft.turnoffs(), MAX_ITEMS, MAX_ITEM_LENGTH),
                confidence,
                evidence
        );
        return new Result(profile, List.copyOf(rejections));
    }

    /**
     * Профиль, который ввёл организатор: те же ограничения, источник — сам организатор.
     */
    public EventAudienceProfile manual(EventAudienceProfileUpdate update) {
        return new EventAudienceProfile(
                summary(update.summary()),
                normalizeLevels(update.levels(), new ArrayList<>()),
                Texts.cleanList(update.roles(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(update.domains(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(update.expectations(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(update.assumedKnowledge(), MAX_ITEMS, MAX_ITEM_LENGTH),
                Texts.cleanList(update.turnoffs(), MAX_ITEMS, MAX_ITEM_LENGTH),
                "high",
                List.of(new Evidence("DEVREL", null, "Уточнено организатором"))
        );
    }

    /**
     * Сколько уверенности дают данные: заявленная аудитория и программа из нескольких докладов — high,
     * хоть что-то сверх названия и темы — medium, иначе low.
     */
    static String confidenceCap(EventAudienceInputs inputs) {
        EventInfo event = inputs.event();
        boolean hasAudience = Texts.trimToNull(event.audience()) != null;
        int accepted = inputs.acceptedTalks() == null ? 0 : inputs.acceptedTalks().size();
        boolean richDescription = event.description() != null && event.description().trim().length() >= RICH_DESCRIPTION_CHARS;
        if (hasAudience && accepted >= 2) {
            return "high";
        }
        if (hasAudience || accepted >= 1 || richDescription) {
            return "medium";
        }
        return "low";
    }

    /**
     * Доли уровней в процентах с суммой ровно 100 (метод наибольшего остатка). Неизвестные уровни отбрасываются.
     */
    static Map<String, Integer> normalizeLevels(Map<String, Object> raw, List<Rejection> rejections) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (raw != null) {
            for (Map.Entry<String, Object> entry : raw.entrySet()) {
                String key = entry.getKey() == null ? "" : entry.getKey().trim().toLowerCase(Locale.ROOT);
                if (key.equals("teamlead") || key.equals("team lead") || key.equals("lead+")) {
                    key = "lead";
                }
                Double value = Texts.number(entry.getValue());
                if (!LEVELS.contains(key)) {
                    rejections.add(new Rejection("Уровень «" + entry.getKey() + "»", "Неизвестный уровень"));
                    continue;
                }
                if (value == null || value < 0) {
                    continue;
                }
                values.merge(key, value, Double::sum);
            }
        }
        double sum = values.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum <= 0) {
            return Map.of();
        }

        Map<String, Integer> result = new LinkedHashMap<>();
        Map<String, Double> remainders = new LinkedHashMap<>();
        int assigned = 0;
        for (String level : LEVELS) {
            double share = values.getOrDefault(level, 0.0) * 100 / sum;
            int floor = (int) Math.floor(share);
            result.put(level, floor);
            remainders.put(level, share - floor);
            assigned += floor;
        }
        List<String> byRemainder = remainders.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .toList();
        for (int i = 0; i < 100 - assigned; i++) {
            String level = byRemainder.get(i % byRemainder.size());
            result.put(level, result.get(level) + 1);
        }
        return result;
    }

    private static List<Evidence> validateEvidence(
            List<EventAudienceProfileDraft.Evidence> drafts,
            EventAudienceInputs inputs,
            List<Rejection> rejections
    ) {
        Map<String, AcceptedTalk> talks = index(inputs.acceptedTalks(), AcceptedTalk::id);
        Map<String, PastEdition> editions = index(inputs.pastEditions(), PastEdition::id);
        List<Evidence> accepted = new ArrayList<>();
        for (EventAudienceProfileDraft.Evidence draft : drafts == null ? List.<EventAudienceProfileDraft.Evidence>of() : drafts) {
            if (draft == null) {
                continue;
            }
            String source = draft.source() == null ? "" : draft.source().trim().toUpperCase(Locale.ROOT);
            String id = Texts.trimToNull(draft.id());
            Evidence resolved = switch (source) {
                case "EVENT" -> {
                    String field = id == null ? null : id.toLowerCase(Locale.ROOT);
                    yield field != null && EVENT_FIELDS.containsKey(field) && Texts.trimToNull(eventField(inputs.event(), field)) != null
                            ? new Evidence(source, field, EVENT_FIELDS.get(field))
                            : null;
                }
                case "ACCEPTED_TALK" -> id != null && talks.containsKey(id)
                        ? new Evidence(source, id, "Доклад программы «" + talks.get(id).name() + "»")
                        : null;
                case "PAST_EDITION" -> id != null && editions.containsKey(id)
                        ? new Evidence(source, id, pastEditionLabel(editions.get(id)))
                        : null;
                default -> null;
            };
            if (resolved == null) {
                rejections.add(new Rejection("Источник " + source + " " + id, "Нет во входных данных"));
                continue;
            }
            if (accepted.contains(resolved)) {
                continue;
            }
            if (accepted.size() >= MAX_EVIDENCE) {
                break;
            }
            accepted.add(resolved);
        }
        return List.copyOf(accepted);
    }

    private static String eventField(EventInfo event, String field) {
        return switch (field) {
            case "name" -> event.name();
            case "topic" -> event.topic();
            case "audience" -> event.audience();
            case "description" -> event.description();
            case "format" -> event.format();
            default -> null;
        };
    }

    private static String pastEditionLabel(PastEdition edition) {
        String company = edition.company() == null ? "" : " (" + edition.company() + ")";
        return "Прошлый выпуск «" + edition.conference() + "»: " + edition.speakerName() + company;
    }

    private static String normalizeConfidence(String raw) {
        String value = raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
        return CONFIDENCE.contains(value) ? value : null;
    }

    private static String summary(String raw) {
        String summary = Texts.trimToNull(raw);
        return summary == null ? null : Texts.truncate(summary, MAX_SUMMARY_LENGTH);
    }

    private static <T> Map<String, T> index(List<T> items, Function<T, String> id) {
        if (items == null) {
            return Map.of();
        }
        return items.stream().filter(Objects::nonNull)
                .collect(Collectors.toMap(id, Function.identity(), (left, right) -> left));
    }
}
