package ru.shaxowskiy.javaspeakerclub.gamification.service;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.FeedbackAnswers;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.PublicFeedbackRequest;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackService;
import ru.shaxowskiy.javaspeakerclub.dto.TalkCreateRequest;
import ru.shaxowskiy.javaspeakerclub.dto.TalkFeedbackCreateRequest;
import ru.shaxowskiy.javaspeakerclub.gamification.GamificationTestData;
import ru.shaxowskiy.javaspeakerclub.gamification.model.BadgeCode;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.CheckInResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.EarnedBadgeResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.GamificationProfileResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.LeaderboardEntry;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.LeagueLeaderboardResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.ManualPointsRequest;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.PointTransactionResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonOutcome;
import ru.shaxowskiy.javaspeakerclub.gamification.model.GamificationModels.SeasonResultResponse;
import ru.shaxowskiy.javaspeakerclub.gamification.model.League;
import ru.shaxowskiy.javaspeakerclub.gamification.model.PointReason;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.GamificationProfileRepository;
import ru.shaxowskiy.javaspeakerclub.dto.TalkResponse;
import ru.shaxowskiy.javaspeakerclub.service.TalkFeedbackService;
import ru.shaxowskiy.javaspeakerclub.service.TalkService;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Без @Transactional: баллы за события начисляются в отдельной транзакции после коммита
 * и не увидели бы незакоммиченных тестовых данных.
 */
@SpringBootTest(properties = GamificationTestData.ISOLATED_H2_URL)
@ActiveProfiles("test")
class GamificationServiceTest {

    @Autowired
    private DSLContext dsl;
    @Autowired
    private TalkService talkService;
    @Autowired
    private TalkFeedbackService feedbackService;
    @Autowired
    private FeedbackService feedbackFormService;
    @Autowired
    private GamificationProfileService profileService;
    @Autowired
    private LeaderboardService leaderboardService;
    @Autowired
    private SeasonCloseService seasonCloseService;
    @Autowired
    private SeasonService seasonService;
    @Autowired
    private GamificationProfileRepository profileRepository;

    private GamificationTestData data;

    @BeforeEach
    void setUp() {
        data = new GamificationTestData(dsl);
        data.clean();
        data.user(1L, "anna", "SPEAKER");
        data.user(2L, "timur", "SPEAKER");
        data.user(3L, "dina", "DEVREL");
    }

    @Test
    @WithMockUser(username = "anna", roles = "SPEAKER")
    void talkCreationAwardsPointsAndDeletionTakesThemBack() {
        TalkResponse talk = talkService.create(talkRequest(1L));

        List<PointTransactionResponse> history = profileService.history(1L, 50);
        assertThat(history).extracting(PointTransactionResponse::reason).containsExactly(PointReason.TALK_CREATED);
        assertThat(history.getFirst().points()).isEqualTo(50);
        assertThat(profileService.getProfile(1L).badges()).extracting(EarnedBadgeResponse::code)
                .contains(BadgeCode.FIRST_TALK);

        talkService.delete(talk.id());

        GamificationProfileResponse profile = profileService.getProfile(1L);
        assertThat(profile.totalPoints()).isZero();
        assertThat(profileService.history(1L, 50)).extracting(PointTransactionResponse::reason)
                .containsExactlyInAnyOrder(PointReason.TALK_CREATED, PointReason.TALK_DELETED);
    }

    @Test
    @WithMockUser(username = "anna", roles = "SPEAKER")
    void feedbackAwardsSpeakerWithBonusForHighRating() {
        TalkResponse talk = talkService.create(talkRequest(1L));

        feedbackService.create(talk.id(), new TalkFeedbackCreateRequest(10, "Отлично", "-", "-", null));
        feedbackService.create(talk.id(), new TalkFeedbackCreateRequest(5, "Норм", "Скучно", "Короче", null));

        // 50 за доклад + (10 + 15) за десятку + 10 за пятёрку
        assertThat(profileService.getProfile(1L).totalPoints()).isEqualTo(85);
        assertThat(profileService.getProfile(1L).badges()).extracting(EarnedBadgeResponse::code)
                .contains(BadgeCode.FIRST_FEEDBACK);
    }

    @Test
    @WithMockUser(username = "anna", roles = "SPEAKER")
    void feedbackFromPublicFormAwardsSpeaker() {
        TalkResponse talk = talkService.create(talkRequest(1L));
        String token = feedbackFormService.getOrCreateLink(talk.id()).token();

        // Лучшие ответы на все пять вопросов дают оценку 10
        feedbackFormService.submit(token, new PublicFeedbackRequest(new FeedbackAnswers(0, 0, 0, 0, 0), null));

        // 50 за доклад + (10 + 15) за десятку
        assertThat(profileService.getProfile(1L).totalPoints()).isEqualTo(75);
        assertThat(profileService.getProfile(1L).badges()).extracting(EarnedBadgeResponse::code)
                .contains(BadgeCode.FIRST_FEEDBACK);
    }

    @Test
    void checkInIsCountedOncePerDayAndExtendsStreak() {
        LocalDate today = seasonService.now().toLocalDate();
        data.streak(1L, 6, today.minusDays(1));

        CheckInResponse first = profileService.checkIn(1L);
        CheckInResponse second = profileService.checkIn(1L);

        assertThat(first.alreadyCheckedIn()).isFalse();
        assertThat(first.currentStreak()).isEqualTo(7);
        assertThat(first.awardedPoints()).isEqualTo(5 + 25);
        assertThat(first.newBadges()).extracting(EarnedBadgeResponse::code).contains(BadgeCode.STREAK_7);
        assertThat(second.alreadyCheckedIn()).isTrue();
        assertThat(second.awardedPoints()).isZero();
        assertThat(profileService.getProfile(1L).checkedInToday()).isTrue();
    }

    @Test
    void streakRestartsAfterMissedDay() {
        data.streak(1L, 10, seasonService.now().toLocalDate().minusDays(3));

        assertThat(profileService.getProfile(1L).currentStreak()).isZero();
        assertThat(profileService.checkIn(1L).currentStreak()).isEqualTo(1);
    }

    @Test
    void nonSpeakerCannotCheckIn() {
        assertThatThrownBy(() -> profileService.checkIn(3L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void manualPointsAreValidatedAndShownInLeaderboard() {
        assertThatThrownBy(() -> profileService.awardManual(1L, new ManualPointsRequest(0, "x"), 3L))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> profileService.awardManual(1L, new ManualPointsRequest(5000, "x"), 3L))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> profileService.awardManual(1L, new ManualPointsRequest(10, " "), 3L))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> profileService.awardManual(3L, new ManualPointsRequest(10, "x"), 3L))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));

        PointTransactionResponse tx = profileService.awardManual(2L, new ManualPointsRequest(120, " Митап "), 3L);
        assertThat(tx.comment()).isEqualTo("Митап");
        assertThat(tx.awardedBy()).isEqualTo(3L);

        LeagueLeaderboardResponse board = leaderboardService.leagueLeaderboard(null, 1L);
        assertThat(board.league()).isEqualTo(League.BRONZE);
        assertThat(board.entries()).extracting(LeaderboardEntry::speakerId).containsExactly(2L, 1L);
        assertThat(board.entries()).extracting(LeaderboardEntry::me).containsExactly(false, true);
        assertThat(profileService.getProfile(2L).weeklyRank()).isEqualTo(1);
    }

    @Test
    void closingSeasonPromotesTopAndDemotesBottomOnce() {
        for (long id = 10; id < 17; id++) {
            data.user(id, "silver" + id, "SPEAKER");
            data.league(id, League.SILVER);
        }
        LocalDateTime start = SeasonService.weekStart(seasonService.now()).minusWeeks(1);
        long seasonId = data.season(start, start.plusWeeks(1));
        // 10 → 70 баллов, 11 → 60, ..., 16 → 10
        for (long id = 10; id < 17; id++) {
            data.points(id, (int) (80 - (id - 9) * 10), start.plusHours(id));
        }
        data.points(1L, 40, start.plusHours(1));

        assertThat(seasonCloseService.closeExpired()).isEqualTo(1);
        assertThat(seasonCloseService.closeExpired()).isZero();

        List<SeasonResultResponse> silver = seasonCloseService.results(seasonId, League.SILVER);
        assertThat(silver).extracting(SeasonResultResponse::speakerId).containsExactly(10L, 11L, 12L, 13L, 14L, 15L, 16L);
        assertThat(silver).extracting(SeasonResultResponse::outcome).containsExactly(
                SeasonOutcome.PROMOTED, SeasonOutcome.PROMOTED, SeasonOutcome.PROMOTED,
                SeasonOutcome.STAYED,
                SeasonOutcome.DEMOTED, SeasonOutcome.DEMOTED, SeasonOutcome.DEMOTED);

        assertThat(league(10L)).isEqualTo(League.GOLD);
        assertThat(league(13L)).isEqualTo(League.SILVER);
        assertThat(league(16L)).isEqualTo(League.BRONZE);
        assertThat(league(1L)).isEqualTo(League.SILVER);
        assertThat(league(2L)).isEqualTo(League.BRONZE);

        assertThat(profileService.getProfile(10L).badges()).extracting(EarnedBadgeResponse::code)
                .contains(BadgeCode.SEASON_WINNER, BadgeCode.SEASON_PODIUM, BadgeCode.PROMOTED, BadgeCode.REACHED_GOLD);
        assertThat(seasonService.current().getStartsAt()).isEqualTo(start.plusWeeks(1));
    }

    @Test
    void resultsOfActiveSeasonAreNotAvailable() {
        Long seasonId = seasonService.current().getId();

        assertThatThrownBy(() -> seasonCloseService.results(seasonId, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private League league(long speakerId) {
        return profileRepository.findBySpeakerId(speakerId)
                .map(profile -> League.valueOf(profile.getLeague()))
                .orElse(League.BRONZE);
    }

    private static TalkCreateRequest talkRequest(long speakerId) {
        LocalDateTime date = LocalDateTime.now().minusDays(1);
        return new TalkCreateRequest(speakerId, "Kotlin coroutines", date, "CONFERENCE", "OFFLINE", "Test Conf", date, null, null);
    }
}
