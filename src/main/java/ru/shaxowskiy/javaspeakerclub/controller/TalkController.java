package ru.shaxowskiy.javaspeakerclub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.shaxowskiy.javaspeakerclub.dto.TalkCreateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.TalkResponse;
import ru.shaxowskiy.javaspeakerclub.dto.TalkTagsAddRequest;
import ru.shaxowskiy.javaspeakerclub.dto.TalkUpdateRequest;
import ru.shaxowskiy.javaspeakerclub.service.TalkService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/talks")
@RequiredArgsConstructor
public class TalkController {

    private final TalkService talkService;

    @GetMapping
    public List<TalkResponse> findAll(@RequestParam(required = false) String conferenceType) {
        if (conferenceType != null && !conferenceType.isBlank()) {
            return talkService.findByConferenceType(conferenceType);
        }
        return talkService.findAll();
    }

    @GetMapping("/{id}")
    public TalkResponse findById(@PathVariable UUID id) {
        return talkService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TalkResponse create(@RequestBody TalkCreateRequest request) {
        return talkService.create(request);
    }

    @PutMapping("/{id}")
    public TalkResponse update(@PathVariable UUID id, @RequestBody TalkUpdateRequest request) {
        return talkService.update(id, request);
    }

    @PatchMapping("/{id}")
    public TalkResponse patch(@PathVariable UUID id, @RequestBody TalkUpdateRequest request) {
        return talkService.patch(id, request);
    }

    @PostMapping("/{id}/tags")
    public TalkResponse addTags(@PathVariable UUID id, @RequestBody TalkTagsAddRequest request) {
        return talkService.addTags(id, request.tags());
    }

    @DeleteMapping("/{id}/tags/{tag}")
    public TalkResponse removeTag(@PathVariable UUID id, @PathVariable String tag) {
        return talkService.removeTag(id, tag);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        talkService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
