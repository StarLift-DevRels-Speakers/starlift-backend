package ru.shaxowskiy.javaspeakerclub.audio;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.security.Principal;
import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/audio-analyses")
@RequiredArgsConstructor
public class AudioAnalysisController {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private final AudioAnalysisRepository jobs;

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Accepted create(@RequestBody Create request, Principal principal) {
        if (request.mediaId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Требуется mediaId");
        }
        UUID id = UUID.randomUUID();
        if (!jobs.create(id, request.mediaId(), principal.getName())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл не найден");
        }
        return new Accepted(id, "QUEUED");
    }

    @GetMapping("/{id}")
    public Status status(@PathVariable UUID id, Principal principal) {
        var job = jobs.find(id, principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Задача не найдена"));
        return new Status(job.id(), job.mediaId(), job.status(),
                job.report() == null ? null : JSON.readTree(job.report()), job.errorCode(),
                job.createdAt(), job.updatedAt());
    }

    public record Create(UUID mediaId) {
    }

    public record Accepted(UUID analysisId, String status) {
    }

    public record Status(UUID analysisId, UUID mediaId, String status, JsonNode report, String errorCode,
                         OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}
