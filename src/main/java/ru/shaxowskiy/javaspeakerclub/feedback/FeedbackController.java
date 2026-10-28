package ru.shaxowskiy.javaspeakerclub.feedback;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.FeedbackLinkResponse;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.PublicFeedbackFormResponse;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.PublicFeedbackRequest;
import ru.shaxowskiy.javaspeakerclub.feedback.FeedbackModels.SpeakerFeedbackResponse;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping("/api/talks/{talkId}/feedback-link")
    public FeedbackLinkResponse link(@PathVariable UUID talkId) {
        return feedbackService.getOrCreateLink(talkId);
    }

    @GetMapping("/api/me/feedback")
    public List<SpeakerFeedbackResponse> mine() {
        return feedbackService.findMine();
    }

    @GetMapping("/api/public/feedback/{token}")
    public PublicFeedbackFormResponse form(@PathVariable String token) {
        return feedbackService.getForm(token);
    }

    @PostMapping("/api/public/feedback/{token}")
    public ResponseEntity<Void> submit(@PathVariable String token, @RequestBody PublicFeedbackRequest request) {
        feedbackService.submit(token, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
