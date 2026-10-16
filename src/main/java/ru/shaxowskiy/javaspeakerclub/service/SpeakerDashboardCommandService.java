package ru.shaxowskiy.javaspeakerclub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.DashboardInfluenceDto;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.SpeakerAchievementResponse;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.SpeakerAchievementUpsertRequest;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.SpeakerCfpLinkResponse;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.SpeakerCfpLinkUpsertRequest;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.SpeakerDashboardProfilePatchRequest;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.SpeakerDashboardProfileResponse;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.SpeakerNpsHistoryCreateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.dashboard.SpeakerNpsHistoryResponse;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerAchievementsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerNpsHistoryRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;
import ru.shaxowskiy.javaspeakerclub.repository.CfpRecommendationRepository;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerAchievementRepository;
import ru.shaxowskiy.javaspeakerclub.repository.SpeakerNpsHistoryRepository;
import ru.shaxowskiy.javaspeakerclub.repository.UserRepository;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SpeakerDashboardCommandService {

    private final UserRepository userRepository;
    private final SpeakerNpsHistoryRepository speakerNpsHistoryRepository;
    private final SpeakerAchievementRepository speakerAchievementRepository;
    private final CfpRecommendationRepository cfpRecommendationRepository;
    private final SpeakerAccessGuard speakerAccessGuard;

    @Transactional
    public SpeakerDashboardProfileResponse patchDashboardProfile(Long speakerId, SpeakerDashboardProfilePatchRequest request) {
        speakerAccessGuard.assertCanManageSpeaker(speakerId);
        UsersRecord current = userRepository.findById(speakerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + speakerId));

        String region = coalesce(request.region(), current.getRegion(), "Другие регионы");
        String company = coalesce(request.company(), current.getCompany(), "Unknown");
        String influence = request.influence() != null ? request.influence().name() : current.getInfluence();
        String avatarColor = coalesce(request.avatarColor(), current.getAvatarColor(), "#0ea5e9");
        boolean topConferenceCandidate = request.topConferenceCandidate() != null
                ? request.topConferenceCandidate()
                : Boolean.TRUE.equals(current.getTopConferenceCandidate());
        LocalDateTime lastEvaluationDate = request.lastEvaluationDate() != null
                ? request.lastEvaluationDate()
                : current.getLastEvaluationDate();
        int profileCompletion = computeProfileCompletion(current, region, company, influence, avatarColor, lastEvaluationDate);

        UsersRecord updated = userRepository.patchDashboardProfile(
                        speakerId,
                        region,
                        company,
                        influence,
                        profileCompletion,
                        topConferenceCandidate,
                        avatarColor,
                        lastEvaluationDate
                )
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + speakerId));

        return toDashboardProfileResponse(updated);
    }

    @Transactional
    public SpeakerNpsHistoryResponse createNpsHistory(Long speakerId, SpeakerNpsHistoryCreateRequest request) {
        speakerAccessGuard.assertCanManageSpeaker(speakerId);
        validateNps(request.nps());

        userRepository.findById(speakerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + speakerId));

        SpeakerNpsHistoryRecord record = speakerNpsHistoryRepository.create(
                speakerId,
                request.nps(),
                request.measuredAt() != null ? request.measuredAt() : LocalDateTime.now(),
                request.source() == null || request.source().isBlank() ? "manual" : request.source().trim()
        );
        return toNpsHistoryResponse(record);
    }

    @Transactional
    public List<SpeakerAchievementResponse> replaceAchievements(Long speakerId, List<SpeakerAchievementUpsertRequest> request) {
        speakerAccessGuard.assertCanManageSpeaker(speakerId);
        userRepository.findById(speakerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + speakerId));

        List<SpeakerAchievementRepository.UpsertInput> payload = request.stream()
                .map(item -> new SpeakerAchievementRepository.UpsertInput(
                        parseUuidOrNull(item.id()),
                        requireText(item.title(), "title"),
                        requireText(item.description(), "description"),
                        requireNonNegative(item.current(), "current"),
                        requirePositive(item.target(), "target")
                ))
                .toList();

        List<SpeakerAchievementsRecord> records = speakerAchievementRepository.replaceForSpeaker(speakerId, payload);
        return records.stream().map(this::toAchievementResponse).toList();
    }

    @Transactional
    public List<SpeakerCfpLinkResponse> replaceSpeakerCfpLinks(Long speakerId, List<SpeakerCfpLinkUpsertRequest> request) {
        speakerAccessGuard.assertCanManageSpeaker(speakerId);
        userRepository.findById(speakerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Speaker not found: " + speakerId));

        List<CfpRecommendationRepository.SpeakerCfpLinkInput> links = request.stream()
                .map(item -> new CfpRecommendationRepository.SpeakerCfpLinkInput(
                        parseUuid(requireText(item.recommendationId(), "recommendationId")),
                        requireMatchScore(item.matchScore())
                ))
                .toList();

        return cfpRecommendationRepository.replaceSpeakerLinks(speakerId, links).stream()
                .map(item -> new SpeakerCfpLinkResponse(
                        item.getSpeakerId(),
                        item.getRecommendationId().toString(),
                        item.getMatchScore(),
                        item.getCreatedDate()
                ))
                .toList();
    }

    @Transactional
    public void recordNpsUpdatePoint(Long speakerId, BigDecimal nps, String source) {
        if (nps == null) {
            return;
        }
        validateNps(nps);
        speakerNpsHistoryRepository.create(
                speakerId,
                nps,
                LocalDateTime.now(),
                source == null || source.isBlank() ? "manual" : source.trim()
        );
    }

    private int computeProfileCompletion(
            UsersRecord current,
            String region,
            String company,
            String influence,
            String avatarColor,
            LocalDateTime lastEvaluationDate
    ) {
        int completed = 0;
        if (!isBlank(region)) completed++;
        if (!isBlank(company)) completed++;
        if (!isBlank(influence)) completed++;
        if (!isBlank(avatarColor)) completed++;
        if (lastEvaluationDate != null) completed++;
        if (!isBlank(current.getFirstName())) completed++;
        if (!isBlank(current.getLastName())) completed++;
        if (!isBlank(current.getSurName())) completed++;
        if (current.getNps() != null) completed++;
        if (current.getAudience() != null && current.getAudience() > 0) completed++;
        return completed * 10;
    }

    private SpeakerDashboardProfileResponse toDashboardProfileResponse(UsersRecord user) {
        return new SpeakerDashboardProfileResponse(
                user.getId(),
                user.getRegion(),
                user.getCompany(),
                parseInfluence(user.getInfluence()),
                user.getProfileCompletion(),
                user.getAudience(),
                user.getLastEvaluationDate(),
                user.getTopConferenceCandidate(),
                user.getAvatarColor()
        );
    }

    private SpeakerNpsHistoryResponse toNpsHistoryResponse(SpeakerNpsHistoryRecord record) {
        return new SpeakerNpsHistoryResponse(
                record.getId(),
                record.getSpeakerId(),
                record.getNps(),
                record.getMeasuredAt(),
                record.getSource()
        );
    }

    private SpeakerAchievementResponse toAchievementResponse(SpeakerAchievementsRecord record) {
        return new SpeakerAchievementResponse(
                record.getId().toString(),
                record.getSpeakerId(),
                record.getTitle(),
                record.getDescription(),
                record.getCurrentValue(),
                record.getTargetValue(),
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

    private void validateNps(BigDecimal nps) {
        if (nps == null || nps.compareTo(BigDecimal.ZERO) < 0 || nps.compareTo(BigDecimal.TEN) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NPS must be between 0 and 10");
        }
    }

    private Integer requireMatchScore(Integer score) {
        if (score == null || score < 0 || score > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "matchScore must be between 0 and 100");
        }
        return score;
    }

    private Integer requireNonNegative(Integer value, String fieldName) {
        if (value == null || value < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be >= 0");
        }
        return value;
    }

    private Integer requirePositive(Integer value, String fieldName) {
        if (value == null || value <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " must be > 0");
        }
        return value;
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid UUID: " + value);
        }
    }

    private UUID parseUuidOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseUuid(value);
    }

    private String coalesce(String value, String fallback, String defaultValue) {
        if (value != null) {
            return value;
        }
        if (fallback != null) {
            return fallback;
        }
        return defaultValue;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
