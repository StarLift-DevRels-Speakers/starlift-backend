package ru.shaxowskiy.javaspeakerclub.dto;

import java.util.List;

public record ProfilePatchRequest(
        String firstName,
        String lastName,
        String surName,
        String about,
        String company,
        String companyRole,
        List<String> tags,
        String avatarMediaKey
) {}
