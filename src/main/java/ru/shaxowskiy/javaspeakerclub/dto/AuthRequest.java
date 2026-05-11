package ru.shaxowskiy.javaspeakerclub.dto;

public record AuthRequest(
        String username,
        String password
) {
}
