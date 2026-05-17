package ru.shaxowskiy.javaspeakerclub.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SpeakerNpsHistoryResponse(
        Long id,
        Long speakerId,
        BigDecimal nps,
        LocalDateTime measuredAt,
        String source
) {
}
