package ru.shaxowskiy.javaspeakerclub.dto;

public record TalkFeedbackCreateRequest(
        Integer rating,
        String positive,
        String negative,
        String improvementSuggestions,
        // participant (по умолчанию), organizer или other; organizer и other может указать только DevRel или администратор
        String authorType
) {}
