package ru.shaxowskiy.javaspeakerclub.producer.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.producer.model.ProducerPlanResponse;
import ru.shaxowskiy.javaspeakerclub.producer.report.ProducerReportService;
import ru.shaxowskiy.javaspeakerclub.producer.service.ProducerService;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/producer")
@RequiredArgsConstructor
public class ProducerController {

    private final ProducerService producerService;
    private final ProducerReportService reportService;
    private final SpeakerAccessGuard accessGuard;

    @GetMapping("/me/plan")
    public ProducerPlanResponse getMyPlan() {
        return producerService.getLatest(accessGuard.currentUserId());
    }

    @PostMapping("/me/plan")
    public ProducerPlanResponse regenerateMyPlan() {
        return producerService.regenerate(accessGuard.currentUserId());
    }

    @GetMapping("/speakers/{speakerId}/plan")
    public ProducerPlanResponse getSpeakerPlan(@PathVariable Long speakerId) {
        accessGuard.assertCanManageSpeaker(speakerId);
        return producerService.getLatest(speakerId);
    }

    @PostMapping("/speakers/{speakerId}/plan")
    public ProducerPlanResponse regenerateSpeakerPlan(@PathVariable Long speakerId) {
        accessGuard.assertCanManageSpeaker(speakerId);
        return producerService.regenerate(speakerId);
    }

    @PostMapping("/plans/regenerate-all")
    public List<ProducerPlanResponse> regenerateAll() {
        return producerService.regenerateAll();
    }

    @GetMapping("/report")
    public ResponseEntity<String> report() {
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(reportService.renderLatestPlans());
    }
}
