package ru.shaxowskiy.javaspeakerclub.gamification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.gamification.GamificationProperties;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.CheckInResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.EarnedBadgeResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.GamificationProfileResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.ManualPointsRequest;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.PointTransactionResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.gamification.model.LeagueZone;
import ru.shaxowskiy.javaspeakerclub.gamification.model.PointReason;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.GamificationProfileRepository;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.PointTransactionRepository;
import ru.shaxowskiy.javaspeakerclub.gamification.service.LeaderboardService.LeagueTable;
import ru.shaxowskiy.javaspeakerclub.gamification.service.LeagueStandings.Standing;
import ru.shaxowskiy.javaspeakerclub.gamification.service.SpeakerDirectory.Participant;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.GamificationPointTransactionsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.GamificationSeasonsRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.SpeakerGamificationProfilesRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.UsersRecord;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GamificationProfileService {

    private static final int MAX_COMMENT_LENGTH = 500;

    private final SpeakerDirectory speakerDirectory;
    private final SeasonService seasonService;
    private final LeaderboardService leaderboardService;
    private final PointsService pointsService;
    private final BadgeService badgeService;
    private final GamificationProfileRepository profileRepository;
    private final PointTransactionRepository transactionRepository;
    private final GamificationProperties properties;

    @Transactional
    public GamificationProfileResponse getProfile(Long speakerId) {
        UsersRecord user = speakerDirectory.requireSpeaker(speakerId);
        GamificationSeasonsRecord season = seasonService.current();
        List<Participant> participants = speakerDirectory.participants();
        League league = participants.stream()
                .filter(participant -> participant.id().equals(speakerId))
                .map(Participant::league)
                .findFirst()
                .orElse(League.BRONZE);

        LeagueTable table = leaderboardService.leagueTable(league, participants, leaderboardService.weeklyTotals(season));
        Standing mine = table.standings().stream()
                .filter(standing -> standing.speakerId().equals(speakerId))
                .findFirst()
                .orElse(new Standing(speakerId, table.standings().size() + 1, 0, LeagueZone.SAFE));

        LocalDate today = seasonService.now().toLocalDate();
        SpeakerGamificationProfilesRecord profile = profileRepository.findBySpeakerId(speakerId).orElse(null);
        LocalDate lastActive = profile == null ? null : profile.getLastActiveDate();

        return new GamificationProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getFirstName(),
                user.getLastName(),
                user.getAvatarColor(),
                league,
                mine.points(),
                mine.rank(),
                table.standings().size(),
                mine.zone(),
                transactionRepository.sumBySpeakerId(speakerId),
                effectiveStreak(profile, today),
                profile == null ? 0 : profile.getLongestStreak(),
                today.equals(lastActive),
                SeasonService.toResponse(season),
                badgeService.findBySpeakerId(speakerId)
        );
    }

    /**
     * Ежедневная отметка: продлевает серию (или начинает заново после пропуска), начисляет баллы
     * за день и бонус за каждые streakBonusEveryDays дней подряд.
     */
    @Transactional
    public CheckInResponse checkIn(Long speakerId) {
        speakerDirectory.requireCurrentUserIsSpeaker(speakerId);
        LocalDateTime now = seasonService.now();
        LocalDate today = now.toLocalDate();
        SpeakerGamificationProfilesRecord profile = profileRepository.lockOrCreate(speakerId, now);

        if (today.equals(profile.getLastActiveDate())) {
            return new CheckInResponse(true, 0, profile.getCurrentStreak(), profile.getLongestStreak(), List.of());
        }

        int streak = today.minusDays(1).equals(profile.getLastActiveDate()) ? profile.getCurrentStreak() + 1 : 1;
        int longest = Math.max(streak, profile.getLongestStreak());
        profileRepository.updateStreak(speakerId, streak, longest, today, now);

        String sourceId = today.toString();
        int awarded = pointsOf(pointsService.award(
                speakerId, PointReason.DAILY_CHECK_IN, properties.points().dailyCheckIn(), sourceId, null, null));
        if (properties.streakBonusEveryDays() > 0 && streak % properties.streakBonusEveryDays() == 0) {
            awarded += pointsOf(pointsService.award(
                    speakerId, PointReason.STREAK_BONUS, properties.points().streakBonus(), sourceId, null, null));
        }

        List<EarnedBadgeResponse> newBadges = badgeService.evaluateActivity(speakerId);
        return new CheckInResponse(false, awarded, streak, longest, newBadges);
    }

    @Transactional(readOnly = true)
    public List<PointTransactionResponse> history(Long speakerId, int limit) {
        speakerDirectory.requireSpeaker(speakerId);
        return transactionRepository.findBySpeakerId(speakerId, limit).stream()
                .map(PointsService::toResponse)
                .toList();
    }

    @Transactional
    public PointTransactionResponse awardManual(Long speakerId, ManualPointsRequest request, Long awardedBy) {
        speakerDirectory.requireSpeaker(speakerId);
        if (request == null || request.points() == null || request.points() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "points must be a non-zero integer");
        }
        if (Math.abs(request.points()) > properties.manualMaxAbs()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "points must be between -" + properties.manualMaxAbs() + " and " + properties.manualMaxAbs());
        }
        if (request.comment() == null || request.comment().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "comment is required");
        }
        String comment = request.comment().trim();
        if (comment.length() > MAX_COMMENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "comment must be at most " + MAX_COMMENT_LENGTH + " characters");
        }

        GamificationPointTransactionsRecord record = pointsService
                .award(speakerId, PointReason.MANUAL, request.points(), null, comment, awardedBy)
                .orElseThrow(() -> new IllegalStateException("Manual points were not saved"));
        badgeService.evaluateActivity(speakerId);
        return PointsService.toResponse(record);
    }

    /**
     * Серия обнуляется, если вчера отметки не было: в базе она остаётся до следующей отметки.
     */
    private static int effectiveStreak(SpeakerGamificationProfilesRecord profile, LocalDate today) {
        if (profile == null || profile.getLastActiveDate() == null) {
            return 0;
        }
        LocalDate last = profile.getLastActiveDate();
        return last.equals(today) || last.equals(today.minusDays(1)) ? profile.getCurrentStreak() : 0;
    }

    private static int pointsOf(java.util.Optional<GamificationPointTransactionsRecord> record) {
        return record.map(GamificationPointTransactionsRecord::getPoints).orElse(0);
    }
}
