package ru.shaxowskiy.javaspeakerclub.dto;

public record ProfilePatchRequest(
        String firstName,
        String lastName,
        String surName,
        String avatarMediaKey
) {}
