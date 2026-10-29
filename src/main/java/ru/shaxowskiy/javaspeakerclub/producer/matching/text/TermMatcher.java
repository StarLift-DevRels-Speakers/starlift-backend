package ru.shaxowskiy.javaspeakerclub.producer.matching.text;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Сравнение тем мероприятия с опытом эксперта без внешних сервисов: основы слов (первые пять букв длинных слов),
 * русско-английские соответствия частых IT-терминов и отсев общих слов. Тема описания «Продуктовая аналитика»
 * так находит доклады с тегами product и analytics, а слово «доклад» ни с чем не совпадает.
 * В теме из двух слов должны совпасть оба, из трёх и больше — хотя бы половина: «Инженерия данных» не находится по одному
 * слову «данных» в докладе «Как говорить с бизнесом на языке данных» (ТЗ: не подбирать по одному совпадению).
 */
public final class TermMatcher {

    private static final Pattern SPLIT = Pattern.compile("[^\\p{L}\\p{N}+#]+");
    private static final int STEM = 5;

    /** Слова, которые есть почти в любом описании и не говорят о теме. */
    private static final Set<String> STOP_WORDS = Set.of(
            "и", "в", "во", "на", "для", "по", "с", "со", "о", "об", "от", "до", "как", "это", "или", "не", "из", "за",
            "к", "у", "а", "но", "что", "чтобы", "при", "без", "про", "the", "and", "of", "for", "in", "on", "with",
            "a", "an", "to", "by", "how", "from", "our", "your",
            "докла", "доклад", "спике", "конфе", "митап", "meetu", "talk", "talks", "мероп", "event", "events",
            "аудит", "участ", "опыт", "кейс", "кейсы", "case", "cases", "основ", "введе", "обзор", "практ", "pract",
            "разра", "devel", "техно", "techn", "систе", "решен", "подхо", "работ", "компа", "будущ", "совре",
            "эффек", "новый", "новые", "новое", "лучши", "best", "часть", "через", "нужен", "нужно", "нужны",
            "наших", "ваших", "очень", "более", "также", "можно", "года", "году", "темы", "тема", "тему");

    /** Русские основы → английские основы тегов платформы. */
    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("проду", "produ"), Map.entry("анали", "analy"), Map.entry("мобил", "mobil"),
            Map.entry("архит", "archi"), Map.entry("данны", "data"), Map.entry("метри", "metri"),
            Map.entry("бэкен", "backe"), Map.entry("бекен", "backe"), Map.entry("фронт", "front"),
            Map.entry("тести", "testi"), Map.entry("тесты", "testi"), Map.entry("безоп", "secur"),
            Map.entry("облак", "cloud"), Map.entry("облач", "cloud"), Map.entry("инфра", "infra"),
            Map.entry("девоп", "devop"), Map.entry("котли", "kotli"), Map.entry("андро", "andro"),
            Map.entry("микро", "micro"), Map.entry("поток", "strea"), Map.entry("стрим", "strea"),
            Map.entry("качес", "quali"), Map.entry("дизай", "desig"), Map.entry("машин", "machi"),
            Map.entry("нейро", "neura"), Map.entry("платф", "platf"), Map.entry("экспе", "exper"),
            Map.entry("менед", "manag"), Map.entry("управ", "manag"), Map.entry("лидер", "leade"),
            Map.entry("коман", "team"), Map.entry("джава", "java"), Map.entry("питон", "pytho"),
            Map.entry("транз", "trans"), Map.entry("распр", "distr"), Map.entry("инжен", "engin"),
            Map.entry("монит", "monit"));

    private TermMatcher() {
    }

    /**
     * Термин запроса: исходная формулировка для объяснений, все ключи и значимые слова — у каждого слова свои ключи
     * (основа и английское соответствие). whole — тег целиком (data-quality), совпадение по нему засчитывает термин.
     */
    public record Term(String label, Set<String> keys, List<Set<String>> words, String whole) {
    }

    public static Term term(String phrase) {
        List<Set<String>> words = new ArrayList<>();
        String whole = null;
        if (phrase != null && !phrase.isBlank()) {
            String normalized = normalize(phrase);
            if (!normalized.contains(" ") && normalized.length() >= 2) {
                whole = normalized;
            }
            for (String token : SPLIT.split(normalized)) {
                Set<String> word = wordKeys(token);
                if (!word.isEmpty() && !words.contains(word)) {
                    words.add(word);
                }
            }
        }
        return new Term(phrase == null ? "" : phrase.trim(), keys(phrase), List.copyOf(words), whole);
    }

    public static List<Term> terms(List<String> phrases) {
        return phrases.stream()
                .map(TermMatcher::term)
                .filter(term -> !term.keys().isEmpty())
                .toList();
    }

    /**
     * Ключи текста: тег целиком (data-quality), основы значимых слов и их английские соответствия.
     */
    public static Set<String> keys(String text) {
        Set<String> keys = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return keys;
        }
        String normalized = normalize(text);
        if (!normalized.contains(" ") && normalized.length() >= 2) {
            keys.add(normalized);
        }
        for (String token : SPLIT.split(normalized)) {
            keys.addAll(wordKeys(token));
        }
        return keys;
    }

    /**
     * Термин из одного-двух слов совпадает, если совпали все слова; из трёх и больше — если совпала хотя бы половина.
     */
    public static boolean matches(Term term, Set<String> evidenceKeys) {
        if (term.whole() != null && evidenceKeys.contains(term.whole())) {
            return true;
        }
        if (term.words().isEmpty()) {
            return term.keys().stream().anyMatch(evidenceKeys::contains);
        }
        long matched = term.words().stream()
                .filter(word -> word.stream().anyMatch(evidenceKeys::contains))
                .count();
        int words = term.words().size();
        long required = words <= 2 ? words : (words + 1) / 2;
        return matched >= required;
    }

    private static Set<String> wordKeys(String token) {
        Set<String> keys = new LinkedHashSet<>();
        if (token.length() < 2 || STOP_WORDS.contains(token)) {
            return keys;
        }
        String stem = stem(token);
        if (STOP_WORDS.contains(stem)) {
            return keys;
        }
        keys.add(stem);
        String translated = SYNONYMS.get(stem);
        if (translated != null) {
            keys.add(translated);
        }
        return keys;
    }

    public static String normalize(String text) {
        return text.trim().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    /** Тег в едином виде: строчные буквы, без лишних пробелов. */
    public static String normalizeTag(String tag) {
        return normalize(tag).replaceAll("\\s+", " ");
    }

    static String stem(String token) {
        return token.length() > STEM ? token.substring(0, STEM) : token;
    }
}
