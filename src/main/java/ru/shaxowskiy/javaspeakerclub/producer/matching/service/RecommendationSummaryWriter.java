package ru.shaxowskiy.javaspeakerclub.producer.matching.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.llm.ProducerLlmClient;
import ru.shaxowskiy.javaspeakerclub.producer.llm.PromptFiles;
import ru.shaxowskiy.javaspeakerclub.producer.matching.MatchingProperties;
import ru.shaxowskiy.javaspeakerclub.producer.matching.model.Recommendation.TalkRef;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * «Код отбирает, ИИ объясняет»: модель пересказывает готовые факты рекомендации одним-двумя предложениями.
 * Пересказ попадает в ответ, только если в нём нет чужих докладов и чисел, которых нет в фактах; иначе остаётся
 * объяснение кода. Модель недоступна — подбор работает как обычно.
 */
@Slf4j
@Component
public class RecommendationSummaryWriter {

    /** Пометки фактов: модель должна отличать плюсы от ограничений, иначе пишет «живёт в Москве — ограничение». */
    public static final String PLUS = "Плюс: ";
    public static final String LIMITATION = "Ограничение: ";
    public static final String MISSING = "Не хватает данных: ";

    public static final String AUDIENCE_ORGANIZER = "organizer";
    public static final String AUDIENCE_EXPERT = "expert";

    private static final int MAX_LENGTH = 400;
    private static final int MAX_ATTEMPTS = 2;
    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern FOREIGN_SCRIPT = Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsHangul}]");
    private static final List<String> PLACEHOLDERS = List.of("<пояснение>", "<id", "id из входа", "1–2 предложения");
    private static final Pattern SECOND_PERSON = Pattern.compile("(?iu)(^|[^\\p{L}])(тво[йеияю]\\p{L}*|тебе|тебя|ты)([^\\p{L}]|$)");
    /** Система не объявляет «лучшего» (п. 11 ТЗ): выбор за человеком, пересказ только объясняет. */
    private static final Pattern VERDICT = Pattern.compile(
            "лучш|идеальн|оптимальн|самый подходящ|самая подходящ|наиболее подходящ|безусловно|гарантир",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final ProducerLlmClient llmClient;
    private final ProducerJson json;
    private final MatchingProperties properties;
    private final String systemPrompt;

    public RecommendationSummaryWriter(ProducerLlmClient llmClient, ProducerJson json, MatchingProperties properties) {
        this.llmClient = llmClient;
        this.json = json;
        this.properties = properties;
        this.systemPrompt = PromptFiles.load(properties.summaryPromptVersion());
    }

    /** Вариант для пересказа: только проверенные факты, которые уже есть в объяснении кода. */
    public record Input(String id, String name, List<String> facts, List<TalkRef> talks) {
    }

    /** Итог для журнала запуска: сколько пересказов принято и почему отброшены остальные. */
    public record Report(boolean attempted, int accepted, List<String> rejected, String model, String promptVersion) {
    }

    public record Result(Map<String, String> summaries, Report report) {
    }

    public record Draft(List<DraftItem> items) {
    }

    public record DraftItem(String id, String summary, List<String> talkIds) {
    }

    public record Request(String audience, List<Input> items) {
    }

    public Result write(String audience, List<Input> inputs) {
        List<Input> limited = inputs.stream().limit(properties.aiSummaryLimit()).toList();
        if (!properties.aiSummaries() || limited.isEmpty()) {
            return new Result(Map.of(), new Report(false, 0, List.of(), null, null));
        }
        Draft draft;
        RuntimeException failure = null;
        String request = json.write(new Request(audience, limited));
        // Небольшие модели иногда ломают JSON или пишут оценки вместо фактов: одна повторная попытка дешевле,
        // чем ответ без пересказов.
        Result result = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                draft = llmClient.generate(systemPrompt, request, Draft.class);
            } catch (RuntimeException e) {
                failure = e;
                log.warn("Recommendation summaries failed (attempt {}): {}", attempt, e.getMessage());
                continue;
            }
            Result candidate = validate(draft, limited, audience);
            if (result == null || candidate.summaries().size() > result.summaries().size()) {
                result = candidate;
            }
            // Вторая попытка — только если пересказ первого варианта не прошёл проверку.
            if (result.summaries().containsKey(limited.get(0).id())) {
                break;
            }
        }
        if (result == null) {
            return new Result(Map.of(), new Report(true, 0, List.of("Модель не ответила: " + failure.getClass().getSimpleName()),
                    llmClient.modelName(), properties.summaryPromptVersion()));
        }
        return new Result(result.summaries(), new Report(true, result.report().accepted(), result.report().rejected(),
                llmClient.modelName(), properties.summaryPromptVersion()));
    }

    static Result validate(Draft draft, List<Input> inputs) {
        return validate(draft, inputs, null);
    }

    static Result validate(Draft draft, List<Input> inputs, String audience) {
        Map<String, Input> byId = new LinkedHashMap<>();
        inputs.forEach(input -> byId.put(input.id(), input));
        Map<String, String> accepted = new LinkedHashMap<>();
        List<String> rejected = new ArrayList<>();
        List<DraftItem> items = draft == null || draft.items() == null ? List.of() : draft.items();
        for (DraftItem item : items) {
            if (item == null || item.id() == null) {
                continue;
            }
            Input input = byId.get(item.id().trim());
            if (input == null) {
                rejected.add("Пересказ для неизвестного варианта " + item.id());
                continue;
            }
            if (accepted.containsKey(input.id())) {
                continue;
            }
            String reason = rejectReason(item, input, audience);
            if (reason != null) {
                rejected.add(input.name() + ": " + reason);
                continue;
            }
            accepted.put(input.id(), item.summary().trim());
        }
        return new Result(accepted, new Report(true, accepted.size(), rejected, null, null));
    }

    private static String rejectReason(DraftItem item, Input input, String audience) {
        String summary = item.summary() == null ? "" : item.summary().trim();
        if (summary.isEmpty()) {
            return "пустой пересказ";
        }
        if (summary.length() > MAX_LENGTH) {
            return "слишком длинный пересказ";
        }
        if (FOREIGN_SCRIPT.matcher(summary).find()) {
            return "текст не на русском";
        }
        String lower = summary.toLowerCase();
        if (PLACEHOLDERS.stream().anyMatch(lower::contains)) {
            return "в пересказе шаблон из инструкции";
        }
        if (VERDICT.matcher(summary).find()) {
            return "оценка вместо фактов («лучший», «идеальный»)";
        }
        if (AUDIENCE_ORGANIZER.equals(audience) && SECOND_PERSON.matcher(summary).find()) {
            return "пересказ обращается к спикеру, а читает организатор";
        }
        boolean hasLimits = input.facts().stream().anyMatch(fact -> fact.startsWith(LIMITATION) || fact.startsWith(MISSING));
        if (!hasLimits && lower.contains("огранич")) {
            return "ограничение, которого нет в фактах";
        }
        Set<String> talkIds = new HashSet<>();
        input.talks().forEach(talk -> talkIds.add(talk.id()));
        List<String> foreignTalks = (item.talkIds() == null ? List.<String>of() : item.talkIds()).stream()
                .filter(Objects::nonNull)
                .filter(id -> !talkIds.contains(id.trim()))
                .toList();
        if (!foreignTalks.isEmpty()) {
            return "ссылка на доклад, которого нет в фактах";
        }
        Set<String> allowed = allowedNumbers(input);
        Matcher matcher = NUMBER.matcher(summary);
        while (matcher.find()) {
            if (!allowed.contains(matcher.group())) {
                return "число " + matcher.group() + ", которого нет в фактах";
            }
        }
        return null;
    }

    /** Числа, которые модель может повторить: всё, что встречается в фактах, докладах и датах докладов. */
    private static Set<String> allowedNumbers(Input input) {
        StringBuilder text = new StringBuilder();
        input.facts().forEach(fact -> text.append(fact).append(' '));
        input.talks().forEach(talk -> text.append(talk.topic()).append(' ').append(talk.event()).append(' ')
                .append(talk.date()).append(' '));
        text.append(input.name());
        Set<String> numbers = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            String number = matcher.group();
            numbers.add(number);
            numbers.add(number.replaceFirst("^0+(?=\\d)", ""));
        }
        return numbers;
    }
}
