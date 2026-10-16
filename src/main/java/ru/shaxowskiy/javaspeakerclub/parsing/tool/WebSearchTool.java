package ru.shaxowskiy.javaspeakerclub.parsing.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import ru.shaxowskiy.javaspeakerclub.config.ParsingProperties;
import ru.shaxowskiy.javaspeakerclub.parsing.model.SerperSearchResponse;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSearchTool {

    private static final int SERPER_FREE_TIER_MAX_NUM_WITH_SITE_OPERATORS = 10;
    private static final String SERPER_GEO = "ru";
    private static final String SERPER_LANGUAGE = "ru";

    private final WebClient parsingWebClient;
    private final ParsingProperties properties;

    public List<String> search(String query) {
        if (properties.searchApiKey() == null || properties.searchApiKey().isBlank()) {
            log.warn("PARSING_SEARCH_API_KEY is not set; skipping web search for query={}", query);
            return List.of();
        }

        try {
            SerperSearchResponse response = parsingWebClient.post()
                    .uri(properties.serperUrl())
                    .header("X-API-KEY", properties.searchApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(serperRequestBody(query))
                    .retrieve()
                    .bodyToMono(SerperSearchResponse.class)
                    .block(properties.requestTimeout());

            if (response == null) {
                return List.of();
            }

            List<String> links = response.links();
            log.info("Serper search returned {} links for query={}", links.size(), query);
            return links;
        } catch (WebClientResponseException e) {
            log.warn("Web search failed for query={}: {} {}", query, e.getStatusCode(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("Web search failed for query={}: {}", query, e.getMessage());
            return List.of();
        }
    }

    public List<String> searchForDate(java.time.LocalDate targetDate, String userHint) {
        String dateRu = targetDate.getDayOfMonth() + " "
                + russianMonth(targetDate.getMonthValue()) + " "
                + targetDate.getYear();

        List<String> queries = List.of(
                "IT конференция программа докладчики " + dateRu,
                "speakers program schedule " + targetDate + " Russia dev conference",
                "спикеры программа митап " + dateRu + " программа докладов"
        );

        Set<String> unique = new LinkedHashSet<>();
        for (String query : queries) {
            String enriched = userHint == null || userHint.isBlank()
                    ? query
                    : query + " " + userHint;
            unique.addAll(search(enriched));
        }
        return List.copyOf(unique);
    }

    private Map<String, Object> serperRequestBody(String query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", query);
        body.put("num", effectiveNum(query));
        body.put("gl", SERPER_GEO);
        body.put("hl", SERPER_LANGUAGE);
        return body;
    }

    private int effectiveNum(String query) {
        int requested = properties.serperResultsPerQuery();
        if (containsSiteOperator(query)) {
            return Math.min(requested, SERPER_FREE_TIER_MAX_NUM_WITH_SITE_OPERATORS);
        }
        return requested;
    }

    private static boolean containsSiteOperator(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        String lower = query.toLowerCase(Locale.ROOT);
        return lower.contains("site:") || lower.contains("-site:");
    }

    private static String russianMonth(int month) {
        return switch (month) {
            case 1 -> "января";
            case 2 -> "февраля";
            case 3 -> "марта";
            case 4 -> "апреля";
            case 5 -> "мая";
            case 6 -> "июня";
            case 7 -> "июля";
            case 8 -> "августа";
            case 9 -> "сентября";
            case 10 -> "октября";
            case 11 -> "ноября";
            case 12 -> "декабря";
            default -> "";
        };
    }
}
