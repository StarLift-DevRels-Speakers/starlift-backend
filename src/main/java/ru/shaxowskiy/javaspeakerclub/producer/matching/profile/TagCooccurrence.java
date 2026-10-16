package ru.shaxowskiy.javaspeakerclub.producer.matching.profile;

import ru.shaxowskiy.javaspeakerclub.producer.matching.text.TermMatcher;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Какие теги платформы встречаются вместе в одном докладе или CFP. Основа смежной экспертизы:
 * android часто рядом с kotlin — значит, kotlin смежен для эксперта с подтверждённым android.
 */
public final class TagCooccurrence {

    private final Map<String, Map<String, Integer>> pairs;

    private TagCooccurrence(Map<String, Map<String, Integer>> pairs) {
        this.pairs = pairs;
    }

    public static TagCooccurrence empty() {
        return new TagCooccurrence(Map.of());
    }

    public static TagCooccurrence from(Collection<List<String>> tagSets) {
        Map<String, Map<String, Integer>> pairs = new HashMap<>();
        for (List<String> set : tagSets) {
            List<String> tags = set.stream().map(TermMatcher::normalizeTag).distinct().toList();
            for (String a : tags) {
                for (String b : tags) {
                    if (!a.equals(b)) {
                        pairs.computeIfAbsent(a, key -> new HashMap<>()).merge(b, 1, Integer::sum);
                    }
                }
            }
        }
        return new TagCooccurrence(pairs);
    }

    /** Связанные теги по убыванию частоты совместного появления. */
    public Map<String, Integer> related(String tag) {
        Map<String, Integer> related = pairs.getOrDefault(TermMatcher.normalizeTag(tag), Map.of());
        Map<String, Integer> sorted = new LinkedHashMap<>();
        related.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }
}
