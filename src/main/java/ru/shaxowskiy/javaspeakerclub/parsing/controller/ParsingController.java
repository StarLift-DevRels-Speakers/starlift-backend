package ru.shaxowskiy.javaspeakerclub.parsing.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.dto.report.ReportModels.ParsingStatus;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsedSpeakerEventResponse;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingEventPatchRequest;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingEventResponse;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingRunAcceptedResponse;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingRunRequest;
import ru.shaxowskiy.javaspeakerclub.parsing.service.ConferenceParsingService;
import ru.shaxowskiy.javaspeakerclub.parsing.service.ParsingRunAsyncExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parsing")
@RequiredArgsConstructor
public class ParsingController {

    private final ConferenceParsingService conferenceParsingService;
    private final ParsingRunAsyncExecutor parsingRunAsyncExecutor;

    @PostMapping("/run")
    public ParsingRunAcceptedResponse run(@RequestBody ParsingRunRequest request) {
        if (request.date() == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "date is required"
            );
        }
        parsingRunAsyncExecutor.execute(request);
        return new ParsingRunAcceptedResponse("Request successful get. The result will become available for near time");
    }

    @GetMapping("/events")
    public List<ParsingEventResponse> findEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ParsingStatus status
    ) {
        return conferenceParsingService.findEvents(date, status);
    }

    @GetMapping("/speakers")
    public List<ParsedSpeakerEventResponse> findParsedSpeakers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return conferenceParsingService.findParsedSpeakers(date);
    }

    @PatchMapping("/events/{id}")
    public ParsingEventResponse patchEvent(
            @PathVariable UUID id,
            @RequestBody ParsingEventPatchRequest request
    ) {
        return conferenceParsingService.patchEvent(id, request);
    }
}
