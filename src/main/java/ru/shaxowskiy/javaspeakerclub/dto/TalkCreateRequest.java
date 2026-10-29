package ru.shaxowskiy.javaspeakerclub.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record TalkCreateRequest(
        Long speakerId,
        String topic,
        LocalDateTime talkDate,
        String conferenceType,
        String format,
        String activityName,
        LocalDateTime activityDate,
        List<String> tags,
        UUID speakerTalkId
) {}
