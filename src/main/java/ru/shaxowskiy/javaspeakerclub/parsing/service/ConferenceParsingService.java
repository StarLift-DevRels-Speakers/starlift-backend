package ru.shaxowskiy.javaspeakerclub.parsing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.TalkCreateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels.ParsingStatus;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.ParsingEventsRecord;
import ru.shaxowskiy.javaspeakerclub.parsing.agent.ConferenceParsingAgent;
import ru.shaxowskiy.javaspeakerclub.parsing.agent.ConferenceParsingAgent.ExtractedCandidate;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsedSpeakerRow;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsedSpeakerEventResponse;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingEventPatchRequest;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingEventResponse;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingRunRequest;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingRunResponse;
import ru.shaxowskiy.javaspeakerclub.parsing.util.ParsedSpeakerRowValidator;
import ru.shaxowskiy.javaspeakerclub.parsing.util.ParsingDedupUtil;
import ru.shaxowskiy.javaspeakerclub.repository.ParsingEventRepository;
import ru.shaxowskiy.javaspeakerclub.service.TalkService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConferenceParsingService {
    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");

    private final ConferenceParsingAgent parsingAgent;
    private final ParsingEventRepository parsingEventRepository;
    private final SpeakerMatchingService speakerMatchingService;
    private final TalkService talkService;
    private final ParsedSpeakerRowValidator speakerRowValidator;
    @Value("${app.parsing.scheduled-run-cron}")
    private String scheduledRunCron;
    @Value("${app.parsing.scheduled-run-enabled:false}")
    private boolean scheduledRunEnabled;

    @Transactional
    @Scheduled(cron = "${app.parsing.scheduled-run-cron}", zone = "Europe/Moscow")
    public void runScheduled() {
        if (!scheduledRunEnabled) {
            log.debug("Scheduled parsing run is disabled, cron={}", scheduledRunCron);
            return;
        }
        LocalDate moscowDate = LocalDate.now(MOSCOW_ZONE);
        try {
            run(new ParsingRunRequest(moscowDate, null));
        } catch (Exception e) {
            log.error("Scheduled parsing run failed for date={} (cron={})", moscowDate, scheduledRunCron, e);
        }
    }

    @Transactional
    public ParsingRunResponse run(ParsingRunRequest request) {
        if (request.date() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date is required");
        }

        ConferenceParsingAgent.AgentRunResult agentResult = parsingAgent.run(request.date(), request.hint());
        int newCount = 0;
        int duplicateCount = 0;
        List<ParsedSpeakerRow> persistedRows = new ArrayList<>();

        for (ExtractedCandidate candidate : agentResult.candidates()) {
            ParsedSpeakerRow row = speakerRowValidator.normalizeRow(candidate.row());
            if (!speakerRowValidator.isValid(row)) {
                continue;
            }
            String dedupKey = ParsingDedupUtil.dedupKey(
                    row.conference(),
                    row.speakerName(),
                    request.date(),
                    candidate.sourceUrl()
            );

            if (parsingEventRepository.findByDedupKey(dedupKey).isPresent()) {
                duplicateCount++;
                continue;
            }

            Long matchedSpeakerId = speakerMatchingService.matchSpeakerId(row.speakerName()).orElse(null);
            parsingEventRepository.insert(
                    UUID.randomUUID(),
                    row.conference(),
                    row.speakerName(),
                    row.company(),
                    candidate.sourceUrl(),
                    request.date(),
                    candidate.region(),
                    ParsingStatus.NEW.name().toLowerCase(),
                    matchedSpeakerId,
                    null,
                    dedupKey
            );
            newCount++;
            persistedRows.add(row);
        }

        log.info("Parsing run completed: new={}, duplicate={}, rejectedPlaceholders={}",
                newCount, duplicateCount, agentResult.rejectedPlaceholders());
        ParsingRunResponse response = agentResult.toResponse(newCount, duplicateCount);
        if (!persistedRows.isEmpty()) {
            return new ParsingRunResponse(
                    persistedRows,
                    response.newEvents(),
                    response.duplicateEvents(),
                    response.scannedUrls(),
                    response.errors(),
                    response.skippedUrls(),
                    response.rejectedPlaceholders()
            );
        }
        return response;
    }

    @Transactional(readOnly = true)
    public List<ParsingEventResponse> findEvents(LocalDate date, ParsingStatus status) {
        String statusValue = null;
        if (status != null) {
            statusValue = status == ParsingStatus.NEW ? "new" : status.name();
        }
        return parsingEventRepository.findByFilters(date, statusValue).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ParsedSpeakerEventResponse> findParsedSpeakers(LocalDate date) {
        Map<String, ParsedSpeakerEventResponse> uniqueSpeakers = new LinkedHashMap<>();
        parsingEventRepository.findSpeakersByDate(date).forEach(record -> {
            String key = (record.getConference() + "|" + record.getSpeakerName() + "|" + record.getCompany() + "|" + record.getEventDate())
                    .toLowerCase();
            uniqueSpeakers.putIfAbsent(key, new ParsedSpeakerEventResponse(
                    record.getConference(),
                    record.getSpeakerName(),
                    record.getCompany(),
                    record.getSourceUrl(),
                    record.getEventDate(),
                    record.getRegion(),
                    record.getCreatedAt()
            ));
        });
        return new ArrayList<>(uniqueSpeakers.values());
    }

    @Transactional
    public ParsingEventResponse patchEvent(UUID id, ParsingEventPatchRequest request) {
        ParsingEventsRecord existing = parsingEventRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsing event not found: " + id));

        if (request.status() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }

        Long speakerId = request.speakerId() != null
                ? request.speakerId()
                : existing.getSpeakerId();

        if (request.status() == ParsingStatus.confirmed) {
            speakerId = resolveSpeakerIdForConfirm(existing, speakerId);
            createTalkFromEvent(existing, speakerId, request.topic());
        }

        String statusValue = toStatusDbValue(request.status());
        ParsingEventsRecord updated = parsingEventRepository.updateStatus(
                id,
                statusValue,
                speakerId,
                request.note()
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parsing event not found: " + id));

        return toResponse(updated);
    }

    private Long resolveSpeakerIdForConfirm(ParsingEventsRecord existing, Long speakerId) {
        if (speakerId != null) {
            return speakerId;
        }
        return speakerMatchingService.matchSpeakerId(existing.getSpeakerName())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "speakerId is required when speaker cannot be matched automatically"
                ));
    }

    private void createTalkFromEvent(ParsingEventsRecord event, Long speakerId, String topic) {
        String talkTopic = topic != null && !topic.isBlank()
                ? topic
                : "Talk at " + event.getConference();
        LocalDateTime eventDateTime = event.getEventDate().atStartOfDay();

        talkService.create(new TalkCreateRequest(
                speakerId,
                talkTopic,
                eventDateTime,
                "CONFERENCE",
                "OFFLINE",
                event.getConference(),
                eventDateTime,
                null
        ));
    }

    private ParsingEventResponse toResponse(ParsingEventsRecord record) {
        return new ParsingEventResponse(
                record.getId(),
                record.getConference(),
                record.getSpeakerName(),
                record.getCompany(),
                record.getSourceUrl(),
                record.getEventDate(),
                record.getRegion(),
                fromStatusDbValue(record.getStatus()),
                record.getSpeakerId(),
                record.getNote(),
                record.getCreatedAt()
        );
    }

    private static String toStatusDbValue(ParsingStatus status) {
        if (status == ParsingStatus.NEW) {
            return "new";
        }
        return status.name();
    }

    private static ParsingStatus fromStatusDbValue(String status) {
        if (status == null) {
            return ParsingStatus.NEW;
        }
        return switch (status) {
            case "new" -> ParsingStatus.NEW;
            case "duplicate" -> ParsingStatus.duplicate;
            case "confirmed" -> ParsingStatus.confirmed;
            case "rejected" -> ParsingStatus.rejected;
            default -> ParsingStatus.valueOf(status.toUpperCase());
        };
    }
}
