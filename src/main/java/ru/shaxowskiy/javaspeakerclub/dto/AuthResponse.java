package ru.shaxowskiy.javaspeakerclub.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuthResponse(
        Long id,
        String username,
        List<String> roles,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate
) {
}
