package ru.shaxowskiy.javaspeakerclub.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record TalkFeedbackResponse(
        UUID id,
        UUID talkId,
        Integer rating,
        String positive,
        String negative,
        String improvementSuggestions,
        LocalDateTime createdDate
) {}
