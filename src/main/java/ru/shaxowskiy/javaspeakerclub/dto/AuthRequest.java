package ru.shaxowskiy.javaspeakerclub.dto;

public record AuthRequest(
        String username,
        String password,
        String firstName,
        String lastName,
        String surName
) {
}
