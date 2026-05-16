package ru.shaxowskiy.javaspeakerclub.parsing.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.shaxowskiy.javaspeakerclub.config.ParsingProperties;
import ru.shaxowskiy.javaspeakerclub.parsing.model.PageExtractionResult;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsedSpeakerRow;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingRunResponse;
import ru.shaxowskiy.javaspeakerclub.parsing.tool.PageFetchTool;
import ru.shaxowskiy.javaspeakerclub.parsing.tool.SpeakerExtractionTool;
import ru.shaxowskiy.javaspeakerclub.parsing.tool.WebSearchTool;
import ru.shaxowskiy.javaspeakerclub.parsing.util.ParsedSpeakerRowValidator;
import ru.shaxowskiy.javaspeakerclub.parsing.util.ParsingUrlFilter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConferenceParsingAgent {

    private final WebSearchTool webSearchTool;
    private final PageFetchTool pageFetchTool;
    private final SpeakerExtractionTool speakerExtractionTool;
    private final ParsingProperties parsingProperties;
    private final ParsingUrlFilter parsingUrlFilter;
    private final ParsedSpeakerRowValidator speakerRowValidator;

    public AgentRunResult run(LocalDate targetDate, String userHint) {
        List<String> errors = new ArrayList<>();
        List<String> scannedUrls = new ArrayList<>();
        Map<String, ExtractedCandidate> uniqueSpeakers = new LinkedHashMap<>();
        int skippedUrls = 0;
        int rejectedPlaceholders = 0;

        List<String> rawUrls = webSearchTool.searchForDate(targetDate, userHint);
        if (rawUrls.isEmpty()) {
            errors.add("No URLs found from web search. Check PARSING_SEARCH_API_KEY.");
        }

        List<String> candidateUrls = parsingUrlFilter.prioritize(rawUrls);
        skippedUrls += rawUrls.size() - candidateUrls.size();

        int limit = parsingProperties.maxPagesPerRun();
        int processed = 0;

        for (String url : candidateUrls) {
            if (processed >= limit) {
                break;
            }

            processed++;
            scannedUrls.add(url);

            String pageText = pageFetchTool.fetchPageText(url);
            if (pageText == null) {
                errors.add("Failed to fetch or empty page: " + url);
                continue;
            }

            PageExtractionResult extraction = speakerExtractionTool.extractFromPage(
                    url, pageText, targetDate, userHint
            );
            if (extraction.skipped() || extraction.speakers().isEmpty()) {
                continue;
            }

            for (ParsedSpeakerRow row : extraction.speakers()) {
                ParsedSpeakerRow normalized = speakerRowValidator.normalizeRow(row);
                if (!speakerRowValidator.isValid(normalized)) {
                    rejectedPlaceholders++;
                    log.info("Rejected placeholder speaker: conference={}, name={}",
                            row.conference(), row.speakerName());
                    continue;
                }

                String conference = normalized.conference();
                String key = conference + "|" + normalized.speakerName().toLowerCase(Locale.ROOT);
                uniqueSpeakers.putIfAbsent(key, new ExtractedCandidate(normalized, url, extraction.region()));
            }
        }

        List<ExtractedCandidate> candidates = List.copyOf(uniqueSpeakers.values());

        log.info("Parsing agent finished: candidates={}, scanned={}, skippedUrls={}, rejectedPlaceholders={}",
                candidates.size(), scannedUrls.size(), skippedUrls, rejectedPlaceholders);
        return new AgentRunResult(candidates, scannedUrls, errors, skippedUrls, rejectedPlaceholders);
    }

    public record ExtractedCandidate(ParsedSpeakerRow row, String sourceUrl, String region) {
    }

    public record AgentRunResult(
            List<ExtractedCandidate> candidates,
            List<String> scannedUrls,
            List<String> errors,
            int skippedUrls,
            int rejectedPlaceholders
    ) {
        public ParsingRunResponse toResponse(int newEvents, int duplicateEvents) {
            List<ParsedSpeakerRow> speakers = candidates.stream()
                    .map(ExtractedCandidate::row)
                    .toList();
            return new ParsingRunResponse(
                    speakers,
                    newEvents,
                    duplicateEvents,
                    scannedUrls,
                    errors,
                    skippedUrls,
                    rejectedPlaceholders
            );
        }
    }
}
