package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.CfpRecommendationResponse;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.CfpRecommendationUpsertRequest;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.DashboardInfluenceDto;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.CfpRecommendationsRecord;
import ru.shaxowskiy.javaspeakerclub.repository.CfpRecommendationRepository;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CfpRecommendationService {

    private final CfpRecommendationRepository cfpRecommendationRepository;

    @Transactional(readOnly = true)
    public List<CfpRecommendationResponse> findAll() {
        return cfpRecommendationRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CfpRecommendationResponse findById(UUID recommendationId) {
        CfpRecommendationsRecord record = cfpRecommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found: " + recommendationId));
        return toResponse(record);
    }

    @Transactional
    public CfpRecommendationResponse create(CfpRecommendationUpsertRequest request) {
        validateRequest(request);
        CfpRecommendationsRecord record = cfpRecommendationRepository.create(toInput(request));
        return toResponse(record);
    }

    @Transactional
    public CfpRecommendationResponse update(UUID recommendationId, CfpRecommendationUpsertRequest request) {
        validateRequest(request);
        CfpRecommendationsRecord record = cfpRecommendationRepository.update(recommendationId, toInput(request))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found: " + recommendationId));
        return toResponse(record);
    }

    @Transactional
    public void delete(UUID recommendationId) {
        if (!cfpRecommendationRepository.softDelete(recommendationId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommendation not found: " + recommendationId);
        }
    }

    private CfpRecommendationRepository.CfpRecommendationUpsertInput toInput(CfpRecommendationUpsertRequest request) {
        return new CfpRecommendationRepository.CfpRecommendationUpsertInput(
                request.title().trim(),
                request.city().trim(),
                request.influenceLevel().name(),
                request.topic().trim(),
                request.deadline(),
                request.hostCompany().trim(),
                request.url().trim(),
                request.active() == null ? Boolean.TRUE : request.active(),
                request.tags()
        );
    }

    private CfpRecommendationResponse toResponse(CfpRecommendationsRecord record) {
        return new CfpRecommendationResponse(
                record.getId().toString(),
                record.getTitle(),
                record.getCity(),
                parseInfluence(record.getInfluenceLevel()),
                record.getTopic(),
                record.getDeadline(),
                record.getHostCompany(),
                record.getUrl(),
                record.getActive(),
                cfpRecommendationRepository.findTagsByRecommendationId(record.getId()),
                record.getCreatedDate()
        );
    }

    private DashboardInfluenceDto parseInfluence(String value) {
        if (value == null) {
            return DashboardInfluenceDto.local;
        }
        return switch (value.toLowerCase()) {
            case "regional" -> DashboardInfluenceDto.regional;
            case "federal" -> DashboardInfluenceDto.federal;
            case "international" -> DashboardInfluenceDto.international;
            default -> DashboardInfluenceDto.local;
        };
    }

    private void validateRequest(CfpRecommendationUpsertRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        requireText(request.title(), "title");
        requireText(request.city(), "city");
        requireText(request.topic(), "topic");
        requireText(request.hostCompany(), "hostCompany");
        requireText(request.url(), "url");
        if (request.influenceLevel() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "influenceLevel is required");
        }
        if (request.deadline() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "deadline is required");
        }
        try {
            URI.create(request.url());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "url must be a valid URI");
        }
    }

    private void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
    }
}
