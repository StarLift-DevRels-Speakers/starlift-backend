package ru.shaxowskiy.javaspeakerclub.gamification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.shaxowskiy.javaspeakerclub.gamification.GamificationProperties;
import ru.shaxowskiy.javaspeakerclub.gamification.event.GamificationEvents.FeedbackCreated;
import ru.shaxowskiy.javaspeakerclub.gamification.event.GamificationEvents.ProfileCompletionChanged;
import ru.shaxowskiy.javaspeakerclub.gamification.event.GamificationEvents.TalkCreated;
import ru.shaxowskiy.javaspeakerclub.gamification.event.GamificationEvents.TalkDeleted;
import ru.shaxowskiy.javaspeakerclub.gamification.model.PointReason;
import ru.shaxowskiy.javaspeakerclub.gamification.repository.PointTransactionRepository;

import java.util.List;

/**
 * Автоматические начисления за события платформы. Каждое событие обрабатывается в своей транзакции
 * после коммита основного сценария, поэтому сбой здесь не откатывает доклад или отзыв.
 */
@Service
@RequiredArgsConstructor
public class GamificationActivityService {

    private static final String PROFILE_SOURCE = "profile";
    private static final List<PointReason> FEEDBACK_REASONS =
            List.of(PointReason.FEEDBACK_RECEIVED, PointReason.HIGH_RATING_FEEDBACK);

    private final SpeakerDirectory speakerDirectory;
    private final PointsService pointsService;
    private final BadgeService badgeService;
    private final SeasonService seasonService;
    private final PointTransactionRepository transactionRepository;
    private final GamificationProperties properties;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTalkCreated(TalkCreated event) {
        if (!speakerDirectory.isSpeaker(event.speakerId())) {
            return;
        }
        pointsService.award(event.speakerId(), PointReason.TALK_CREATED, properties.points().talkCreated(),
                event.talkId().toString(), null, null);
        badgeService.evaluateActivity(event.speakerId());
    }

    /**
     * Списывает баллы, начисленные за удалённый доклад, чтобы их нельзя было набить созданием и удалением.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTalkDeleted(TalkDeleted event) {
        String sourceId = event.talkId().toString();
        transactionRepository.findBySource(event.speakerId(), PointReason.TALK_CREATED, sourceId)
                .ifPresent(created -> pointsService.award(event.speakerId(), PointReason.TALK_DELETED,
                        -created.getPoints(), sourceId, null, null));
    }

    /**
     * Отзывы анонимные, поэтому баллы за них ограничены дневным лимитом feedbackDailyCap.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onFeedbackCreated(FeedbackCreated event) {
        Long speakerId = event.speakerId();
        if (!speakerDirectory.isSpeaker(speakerId)) {
            return;
        }
        String sourceId = event.feedbackId().toString();
        if (feedbackPointsToday(speakerId) < properties.feedbackDailyCap()) {
            pointsService.award(speakerId, PointReason.FEEDBACK_RECEIVED, properties.points().feedbackReceived(),
                    sourceId, null, null);
            if (event.rating() >= properties.highRatingThreshold()) {
                pointsService.award(speakerId, PointReason.HIGH_RATING_FEEDBACK, properties.points().highRatingFeedback(),
                        sourceId, null, null);
            }
        }
        badgeService.evaluateActivity(speakerId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProfileCompletionChanged(ProfileCompletionChanged event) {
        if (event.profileCompletion() < 100 || !speakerDirectory.isSpeaker(event.speakerId())) {
            return;
        }
        pointsService.award(event.speakerId(), PointReason.PROFILE_COMPLETED, properties.points().profileCompleted(),
                PROFILE_SOURCE, null, null);
        badgeService.evaluateActivity(event.speakerId());
    }

    private int feedbackPointsToday(Long speakerId) {
        return transactionRepository.sumBySpeakerAndReasonsSince(
                speakerId, FEEDBACK_REASONS, seasonService.now().toLocalDate().atStartOfDay());
    }
}
