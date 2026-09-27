package ru.shaxowskiy.javaspeakerclub.producer.report;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.producer.ProducerJson;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlan;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Эталон демо-данных: какие паттерны заложены в отзывы каждого демо-спикера.
 * Отчёт сверяет с ним план и показывает, что нашлось, что нет и что лишнее.
 */
@Component
public class DemoGolden {

    static final String RESOURCE = "producer/demo-golden.json";
    private static final int MIN_OVERLAP = 2;

    private final Map<String, List<Pattern>> patternsByUsername;

    @Autowired
    public DemoGolden(ProducerJson json) {
        this.patternsByUsername = load(json);
    }

    DemoGolden(Map<String, List<Pattern>> patternsByUsername) {
        this.patternsByUsername = patternsByUsername;
    }

    public record File(Map<String, List<Pattern>> speakers) {
    }

    /**
     * kind: strength или growth. liveDemo — паттерн, который должен появиться только после живого шага демо.
     */
    public record Pattern(String kind, String pattern, List<String> feedbackIds, boolean liveDemo) {
    }

    public record PatternCheck(String kind, String pattern, boolean liveDemo, boolean found, String matchedTheme) {
    }

    public record Check(List<PatternCheck> patterns, List<String> unplannedThemes, int expected, int found) {
    }

    public boolean covers(String username) {
        return patternsByUsername.containsKey(username);
    }

    public Check check(String username, ProducerPlan plan) {
        List<Pattern> patterns = patternsByUsername.getOrDefault(username, List.of());
        List<ProducerPlan.Theme> strengths = plan == null ? List.of() : plan.strengths();
        List<ProducerPlan.Theme> growth = plan == null ? List.of() : plan.growthAreas();

        List<PatternCheck> checks = new ArrayList<>();
        for (Pattern pattern : patterns) {
            List<ProducerPlan.Theme> candidates = "strength".equals(pattern.kind()) ? strengths : growth;
            String matched = candidates.stream()
                    .filter(theme -> overlap(theme, pattern) >= MIN_OVERLAP)
                    .map(ProducerPlan.Theme::title)
                    .findFirst()
                    .orElse(null);
            checks.add(new PatternCheck(pattern.kind(), pattern.pattern(), pattern.liveDemo(), matched != null, matched));
        }

        List<String> unplanned = new ArrayList<>();
        collectUnplanned(strengths, "strength", "Сильная сторона", patterns, unplanned);
        collectUnplanned(growth, "growth", "Зона роста", patterns, unplanned);

        int expected = (int) checks.stream().filter(check -> !check.liveDemo()).count();
        int found = (int) checks.stream().filter(check -> !check.liveDemo() && check.found()).count();
        return new Check(checks, unplanned, expected, found);
    }

    private static void collectUnplanned(List<ProducerPlan.Theme> themes, String kind, String label,
                                         List<Pattern> patterns, List<String> target) {
        for (ProducerPlan.Theme theme : themes) {
            boolean planned = patterns.stream()
                    .filter(pattern -> kind.equals(pattern.kind()))
                    .anyMatch(pattern -> overlap(theme, pattern) >= MIN_OVERLAP);
            if (!planned) {
                target.add(label + " «" + theme.title() + "»");
            }
        }
    }

    private static int overlap(ProducerPlan.Theme theme, Pattern pattern) {
        Set<String> ids = new HashSet<>(theme.feedbackIds());
        ids.retainAll(new HashSet<>(pattern.feedbackIds()));
        return ids.size();
    }

    private static Map<String, List<Pattern>> load(ProducerJson json) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            return Map.of();
        }
        try (InputStream in = resource.getInputStream()) {
            File file = json.read(new String(in.readAllBytes(), StandardCharsets.UTF_8), File.class);
            return file.speakers() == null ? Map.of() : file.speakers();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + RESOURCE, e);
        }
    }
}
