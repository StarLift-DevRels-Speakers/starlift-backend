package ru.shaxowskiy.javaspeakerclub.dto.dashboard;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SpeakerNpsHistoryCreateRequest(
        BigDecimal nps,
        LocalDateTime measuredAt,
        String source
) {
}
