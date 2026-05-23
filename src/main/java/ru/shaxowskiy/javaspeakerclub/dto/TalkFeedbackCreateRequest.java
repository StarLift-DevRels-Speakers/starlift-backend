package ru.shaxowskiy.javaspeakerclub.dto;

public record TalkFeedbackCreateRequest(
        Integer rating,
        String positive,
        String negative,
        String improvementSuggestions
) {}
