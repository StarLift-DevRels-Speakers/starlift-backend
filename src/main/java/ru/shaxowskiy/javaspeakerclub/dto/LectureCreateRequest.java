package ru.shaxowskiy.javaspeakerclub.dto;

import java.util.UUID;

public record LectureCreateRequest(
        String title,
        UUID talkId,
        Long speakerId
) {}
