package ru.shaxowskiy.javaspeakerclub.gamification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.shaxowskiy.javaspeakerclub.gamification.model.BadgeCode;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.EarnedBadgeResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.BadgeRepository;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.GamificationProfileRepository;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.PointTransactionRepository;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.SpeakerActivityStatsRepository;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerBadgesRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerGamificationProfilesRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeRepository badgeRepository;
    private final SpeakerActivityStatsRepository statsRepository;
    private final GamificationProfileRepository profileRepository;
    private final PointTransactionRepository transactionRepository;
    private final SeasonService seasonService;

    /**
     * Проверяет пороги разовых значков за активность и выдаёт недостающие.
     *
     * @return только что выданные значки
     */
    public List<EarnedBadgeResponse> evaluateActivity(Long speakerId) {
        int talks = statsRepository.countTalks(speakerId);
        int feedback = statsRepository.countFeedback(speakerId);
        int longestStreak = profileRepository.findBySpeakerId(speakerId)
                .map(SpeakerGamificationProfilesRecord::getLongestStreak)
                .orElse(0);

        Map<BadgeCode, Boolean> earned = new LinkedHashMap<>();
        earned.put(BadgeCode.FIRST_TALK, talks >= 1);
        earned.put(BadgeCode.TALKS_10, talks >= 10);
        earned.put(BadgeCode.FIRST_FEEDBACK, feedback >= 1);
        earned.put(BadgeCode.FEEDBACK_50, feedback >= 50);
        earned.put(BadgeCode.STREAK_7, longestStreak >= 7);
        earned.put(BadgeCode.STREAK_30, longestStreak >= 30);
        earned.put(BadgeCode.PROFILE_COMPLETE, statsRepository.profileCompletion(speakerId) >= 100);
        earned.put(BadgeCode.POINTS_1000, transactionRepository.sumBySpeakerId(speakerId) >= 1000);

        List<EarnedBadgeResponse> awarded = new ArrayList<>();
        earned.forEach((code, reached) -> {
            if (reached) {
                award(speakerId, code, null).ifPresent(awarded::add);
            }
        });
        return awarded;
    }

    /**
     * Значки за переход в лигу: при попадании сразу в высокую лигу выдаются и значки нижних.
     */
    public void awardReachedLeague(Long speakerId, League league) {
        for (League reached : League.values()) {
            if (reached.compareTo(league) > 0) {
                break;
            }
            BadgeCode code = BadgeCode.forReachedLeague(reached);
            if (code != null) {
                award(speakerId, code, null);
            }
        }
    }

    public Optional<EarnedBadgeResponse> award(Long speakerId, BadgeCode code, Long seasonId) {
        return badgeRepository.awardIfAbsent(speakerId, code, seasonId, seasonService.now())
                .map(BadgeService::toResponse);
    }

    public List<EarnedBadgeResponse> findBySpeakerId(Long speakerId) {
        return badgeRepository.findBySpeakerId(speakerId).stream().map(BadgeService::toResponse).toList();
    }

    private static EarnedBadgeResponse toResponse(SpeakerBadgesRecord record) {
        BadgeCode code = BadgeCode.valueOf(record.getCode());
        return new EarnedBadgeResponse(
                code,
                code.title(),
                code.description(),
                code.category(),
                record.getSeasonId(),
                record.getAwardedDate()
        );
    }
}
