package ru.shaxowskiy.javaspeakerclub.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record LectureResponse(
        UUID id,
        String title,
        UUID talkId,
        Long speakerId,
        boolean hasMedia,
        String mediaFilename,
        String mediaMime,
        LocalDateTime createdDate
) {}
