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
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingEventPatchRequest;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingEventResponse;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingRunRequest;
import ru.shaxowskiy.javaspeakerclub.parsing.model.ParsingRunResponse;
import ru.shaxowskiy.javaspeakerclub.parsing.service.ConferenceParsingService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/parsing")
@RequiredArgsConstructor
public class ParsingController {

    private final ConferenceParsingService conferenceParsingService;

    @PostMapping("/run")
    public ParsingRunResponse run(@RequestBody ParsingRunRequest request) {
        return conferenceParsingService.run(request);
    }

    @GetMapping("/events")
    public List<ParsingEventResponse> findEvents(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) ParsingStatus status
    ) {
        return conferenceParsingService.findEvents(date, status);
    }

    @PatchMapping("/events/{id}")
    public ParsingEventResponse patchEvent(
            @PathVariable UUID id,
            @RequestBody ParsingEventPatchRequest request
    ) {
        return conferenceParsingService.patchEvent(id, request);
    }
}
