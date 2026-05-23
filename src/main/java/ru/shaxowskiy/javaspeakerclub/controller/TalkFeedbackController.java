package ru.shaxowskiy.javaspeakerclub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.dto.TalkFeedbackCreateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.TalkFeedbackResponse;
import ru.shaxowskiy.javaspeakerclub.service.TalkFeedbackService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/talks/{talkId}/feedback")
@RequiredArgsConstructor
public class TalkFeedbackController {

    private final TalkFeedbackService talkFeedbackService;

    @GetMapping
    public List<TalkFeedbackResponse> findAll(@PathVariable UUID talkId) {
        return talkFeedbackService.findAllByTalkId(talkId);
    }

    @GetMapping("/{id}")
    public TalkFeedbackResponse findById(@PathVariable UUID talkId, @PathVariable UUID id) {
        return talkFeedbackService.findByIdAndTalkId(id, talkId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TalkFeedbackResponse create(@PathVariable UUID talkId, @RequestBody TalkFeedbackCreateRequest request) {
        return talkFeedbackService.create(talkId, request);
    }
}
