package ru.shaxowskiy.javaspeakerclub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.dto.SpeakerTalkRequest;
import ru.shaxowskiy.javaspeakerclub.dto.SpeakerTalkResponse;
import ru.shaxowskiy.javaspeakerclub.service.SpeakerTalkService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/speaker-talks")
@RequiredArgsConstructor
public class SpeakerTalkController {

    private final SpeakerTalkService speakerTalkService;

    @GetMapping
    public List<SpeakerTalkResponse> findMine() {
        return speakerTalkService.findMine();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpeakerTalkResponse create(@RequestBody SpeakerTalkRequest request) {
        return speakerTalkService.create(request);
    }

    @PutMapping("/{id}")
    public SpeakerTalkResponse update(@PathVariable UUID id, @RequestBody SpeakerTalkRequest request) {
        return speakerTalkService.update(id, request);
    }
}
