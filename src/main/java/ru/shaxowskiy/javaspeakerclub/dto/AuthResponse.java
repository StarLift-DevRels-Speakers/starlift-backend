package ru.shaxowskiy.javaspeakerclub.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuthResponse(
        Long id,
        String username,
        String firstName,
        String lastName,
        String surName,
        List<String> roles,
        LocalDateTime createdDate,
        LocalDateTime lastModifiedDate
) {
}
