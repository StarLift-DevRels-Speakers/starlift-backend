package ru.shaxowskiy.javaspeakerclub.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.FeedbackAnswers;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.FeedbackLinkResponse;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.PublicFeedbackFormResponse;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.PublicFeedbackRequest;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.SpeakerFeedbackResponse;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalkFeedbackRecord;
import ru.shaxowskiy.javaspeakerclub.jooq.tables.records.TalksRecord;
import ru.shaxowskiy.javaspeakerclub.notification.NotificationService;
import ru.shaxowskiy.javaspeakerclub.repository.TalkFeedbackRepository;
import ru.shaxowskiy.javaspeakerclub.repository.TalkRepository;
import ru.shaxowskiy.javaspeakerclub.security.SpeakerAccessGuard;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Публичная форма отзыва по ссылке на конкретное выступление и отзывы спикера.
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    static final int MAX_COMMENT_LENGTH = 4000;
    private static final int TOKEN_BYTES = 20;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final TalkRepository talkRepository;
    private final TalkFeedbackRepository feedbackRepository;
    private final SpeakerAccessGuard accessGuard;
    private final NotificationService notificationService;

    /**
     * Ссылка на форму отзыва по выступлению; повторный вызов возвращает ту же ссылку.
     */
    @Transactional
    public FeedbackLinkResponse getOrCreateLink(UUID talkId) {
        TalksRecord talk = talkRepository.findById(talkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Выступление не найдено"));
        if (!talk.getSpeakerId().equals(accessGuard.currentUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Выступление не найдено");
        }
        String token = talk.getFeedbackToken();
        if (token == null) {
            token = newToken();
            talkRepository.setFeedbackToken(talkId, token);
        }
        return new FeedbackLinkResponse(talkId, token);
    }

    @Transactional(readOnly = true)
    public PublicFeedbackFormResponse getForm(String token) {
        TalksRecord talk = requireTalkByToken(token);
        return new PublicFeedbackFormResponse(
                talk.getTopic(),
                talk.getActivityName(),
                talk.getTalkDate() == null ? null : talk.getTalkDate().toLocalDate()
        );
    }

    /**
     * Сохраняет ответы формы. rating — среднее по вопросам (лучший ответ 10, средний 5, худший 0),
     * текстовые поля собираются из подписей ответов, чтобы отзыв читали продюсер и weak-signals.
     */
    @Transactional
    public void submit(String token, PublicFeedbackRequest request) {
        TalksRecord talk = requireTalkByToken(token);
        short[] answers = validateAnswers(request == null ? null : request.answers());
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().trim();
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Комментарий длиннее " + MAX_COMMENT_LENGTH + " символов");
        }

        feedbackRepository.createFromForm(
                talk.getId(),
                rating(answers),
                labels(answers, true),
                labels(answers, false),
                comment == null ? "" : comment,
                answers,
                comment
        );
        notificationService.notify(talk.getSpeakerId(), "Новый отзыв слушателя");
    }

    @Transactional(readOnly = true)
    public List<SpeakerFeedbackResponse> findMine() {
        return findBySpeakerId(accessGuard.currentUserId());
    }

    @Transactional(readOnly = true)
    public List<SpeakerFeedbackResponse> findBySpeakerId(Long speakerId) {
        return feedbackRepository.findBySpeakerId(speakerId).stream()
                .map(FeedbackService::toResponse)
                .toList();
    }

    static int rating(short[] answers) {
        double average = 0;
        for (short answer : answers) {
            average += (2 - answer) * 5;
        }
        return (int) Math.round(average / answers.length);
    }

    /**
     * Подписи выбранных ответов: лучших (best) или всех остальных — для positive и negative.
     */
    private static String labels(short[] answers, boolean best) {
        FeedbackQuestion[] questions = FeedbackQuestion.values();
        List<String> picked = new ArrayList<>();
        for (int i = 0; i < questions.length; i++) {
            if ((answers[i] == 0) == best) {
                picked.add(questions[i].label(answers[i]));
            }
        }
        return String.join("; ", picked);
    }

    private static short[] validateAnswers(FeedbackAnswers answers) {
        if (answers == null) {
            throw incomplete();
        }
        Integer[] values = {answers.utility(), answers.clarity(), answers.examples(), answers.pace(), answers.engagement()};
        short[] result = new short[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == null || values[i] < 0 || values[i] > 2) {
                throw incomplete();
            }
            result[i] = values[i].shortValue();
        }
        return result;
    }

    private TalksRecord requireTalkByToken(String token) {
        return talkRepository.findByFeedbackToken(token)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ссылка недействительна"));
    }

    private static SpeakerFeedbackResponse toResponse(TalkFeedbackRecord record) {
        FeedbackAnswers answers = record.getAnswerUtility() == null ? null : new FeedbackAnswers(
                toInt(record.getAnswerUtility()),
                toInt(record.getAnswerClarity()),
                toInt(record.getAnswerExamples()),
                toInt(record.getAnswerPace()),
                toInt(record.getAnswerEngagement())
        );
        return new SpeakerFeedbackResponse(
                record.getId(),
                record.getTalkId(),
                record.getCreatedDate(),
                record.getRating(),
                answers,
                record.getComment(),
                record.getPositive(),
                record.getNegative(),
                record.getImprovementSuggestions()
        );
    }

    private static Integer toInt(Short value) {
        return value == null ? null : value.intValue();
    }

    private static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static ResponseStatusException incomplete() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ответьте на все пять вопросов");
    }
}
