package ru.shaxowskiy.javaspeakerclub.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.CfpRecommendationResponse;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.CfpRecommendationUpsertRequest;
import ru.shaxowskiy.javaspeakerclub.service.CfpRecommendationService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cfp/recommendations")
@RequiredArgsConstructor
public class CfpRecommendationController {

    private final CfpRecommendationService cfpRecommendationService;

    @GetMapping
    public List<CfpRecommendationResponse> findAll() {
        return cfpRecommendationService.findAll();
    }

    @GetMapping("/{id}")
    public CfpRecommendationResponse findById(@PathVariable UUID id) {
        return cfpRecommendationService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CfpRecommendationResponse create(@RequestBody CfpRecommendationUpsertRequest request) {
        return cfpRecommendationService.create(request);
    }

    @PutMapping("/{id}")
    public CfpRecommendationResponse update(@PathVariable UUID id, @RequestBody CfpRecommendationUpsertRequest request) {
        return cfpRecommendationService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        cfpRecommendationService.delete(id);
    }
}
