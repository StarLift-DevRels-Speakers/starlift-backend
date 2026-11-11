package ru.shaxowskiy.javaspeakerclub.audio;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.service.MinioService;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

@RestController
@RequestMapping("/internal/audio-analyses")
public class AudioAnalysisWorkerController {

    private final AudioAnalysisRepository jobs;
    private final MinioService minio;
    private final boolean internalDownload;

    public AudioAnalysisWorkerController(AudioAnalysisRepository jobs, MinioService minio,
                                         @Value("${app.audio.internal-download:false}") boolean internalDownload) {
        this.jobs = jobs;
        this.minio = minio;
        this.internalDownload = internalDownload;
    }

    @PostMapping("/claim")
    @Transactional
    public ResponseEntity<Assignment> claim() {
        jobs.expireExhausted();
        UUID leaseId = UUID.randomUUID();
        return jobs.claim(leaseId)
                .map(job -> ResponseEntity.ok(new Assignment(
                        job.id(), leaseId, internalDownload
                                ? minio.getInternalPresignedUrl(job.objectKey())
                                : minio.getPresignedUrl(job.objectKey()))))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{id}/result")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void finish(@PathVariable UUID id, @RequestBody Result result) {
        boolean success = result.report() != null && result.report().isObject()
                && result.errorCode() == null;
        boolean failure = result.report() == null && result.errorCode() != null;
        if (result.leaseId() == null || (!success && !failure)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный результат анализа");
        }
        if (!jobs.finish(id, result.leaseId(), success ? "SUCCEEDED" : "FAILED",
                success ? result.report().toString() : null,
                failure ? result.errorCode().name() : null)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Задача больше не принадлежит воркеру");
        }
    }

    public record Assignment(UUID analysisId, UUID leaseId, String audioUrl) {
    }

    public record Result(UUID leaseId, JsonNode report, Failure errorCode) {
    }

    public enum Failure {
        DOWNLOAD_FAILED, INVALID_AUDIO, ANALYSIS_FAILED, ANALYSIS_TIMEOUT
    }
}
