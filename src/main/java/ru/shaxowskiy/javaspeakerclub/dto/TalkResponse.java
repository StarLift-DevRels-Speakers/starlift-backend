package ru.shaxowskiy.javaspeakerclub.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TalkResponse(
        UUID id,
        Long speakerId,
        String speakerUsername,
        String speakerFirstName,
        String speakerLastName,
        String speakerSurName,
        String topic,
        LocalDateTime talkDate,
        String conferenceType,
        String format,
        String activityName,
        LocalDateTime activityDate,
        LocalDateTime createdDate,
        List<String> tags,
        List<LectureResponse> lectures
) {}
