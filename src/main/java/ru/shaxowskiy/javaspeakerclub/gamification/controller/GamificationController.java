package ru.shaxowskiy.javaspeakerclub.gamification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.gamification.model.BadgeCode;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.BadgeDefinitionResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.CheckInResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.GamificationProfileResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.GlobalLeaderboardResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.LeaderboardPeriod;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.LeagueLeaderboardResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.LeagueResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.ManualPointsRequest;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.PointTransactionResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonCloseResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonResultResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.gamification.service.GamificationProfileService;
import ru.shaxowskiy.javaspeakerclub.gamification.service.LeaderboardService;
import ru.shaxowskiy.javaspeakerclub.gamification.service.SeasonCloseService;
import ru.shaxowskiy.javaspeakerclub.gamification.service.SeasonService;
import ru.shaxowskiy.javaspeakerclub.gamification.service.SpeakerDirectory;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/gamification")
@RequiredArgsConstructor
public class GamificationController {

    private static final int MAX_LIMIT = 200;

    private final GamificationProfileService profileService;
    private final LeaderboardService leaderboardService;
    private final SeasonService seasonService;
    private final SeasonCloseService seasonCloseService;
    private final SpeakerDirectory speakerDirectory;
    private final SpeakerAccessGuard accessGuard;

    @GetMapping("/me")
    public GamificationProfileResponse getMyProfile() {
        Long userId = accessGuard.currentUserId();
        speakerDirectory.requireCurrentUserIsSpeaker(userId);
        return profileService.getProfile(userId);
    }

    @PostMapping("/me/check-in")
    public CheckInResponse checkIn() {
        return profileService.checkIn(accessGuard.currentUserId());
    }

    @GetMapping("/me/points")
    public List<PointTransactionResponse> getMyPoints(@RequestParam(defaultValue = "50") int limit) {
        Long userId = accessGuard.currentUserId();
        speakerDirectory.requireCurrentUserIsSpeaker(userId);
        return profileService.history(userId, validLimit(limit));
    }

    @GetMapping("/speakers/{speakerId}")
    public GamificationProfileResponse getSpeakerProfile(@PathVariable Long speakerId) {
        return profileService.getProfile(speakerId);
    }

    @GetMapping("/speakers/{speakerId}/points")
    public List<PointTransactionResponse> getSpeakerPoints(@PathVariable Long speakerId,
                                                           @RequestParam(defaultValue = "50") int limit) {
        accessGuard.assertCanManageSpeaker(speakerId);
        return profileService.history(speakerId, validLimit(limit));
    }

    @PostMapping("/speakers/{speakerId}/points")
    @ResponseStatus(HttpStatus.CREATED)
    public PointTransactionResponse awardPoints(@PathVariable Long speakerId, @RequestBody ManualPointsRequest request) {
        return profileService.awardManual(speakerId, request, accessGuard.currentUserId());
    }

    @GetMapping("/leagues")
    public List<LeagueResponse> leagues() {
        return leaderboardService.leagues();
    }

    @GetMapping("/leaderboard/league")
    public LeagueLeaderboardResponse leagueLeaderboard(@RequestParam(required = false) League league) {
        return leaderboardService.leagueLeaderboard(league, accessGuard.currentUserId());
    }

    @GetMapping("/leaderboard/global")
    public GlobalLeaderboardResponse globalLeaderboard(@RequestParam(defaultValue = "ALL_TIME") LeaderboardPeriod period,
                                                       @RequestParam(defaultValue = "50") int limit) {
        return leaderboardService.globalLeaderboard(period, validLimit(limit), accessGuard.currentUserId());
    }

    @GetMapping("/badges")
    public List<BadgeDefinitionResponse> badges() {
        return Arrays.stream(BadgeCode.values()).map(BadgeDefinitionResponse::of).toList();
    }

    @GetMapping("/seasons")
    public List<SeasonResponse> seasons(@RequestParam(defaultValue = "50") int limit) {
        return seasonService.recent(validLimit(limit));
    }

    @GetMapping("/seasons/current")
    public SeasonResponse currentSeason() {
        return seasonService.currentResponse();
    }

    @PostMapping("/seasons/current/close")
    public SeasonCloseResponse closeCurrentSeason() {
        return seasonCloseService.closeCurrent();
    }

    @GetMapping("/seasons/{seasonId}/results")
    public List<SeasonResultResponse> seasonResults(@PathVariable Long seasonId,
                                                    @RequestParam(required = false) League league) {
        return seasonCloseService.results(seasonId, league);
    }

    private static int validLimit(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and " + MAX_LIMIT);
        }
        return limit;
    }
}
