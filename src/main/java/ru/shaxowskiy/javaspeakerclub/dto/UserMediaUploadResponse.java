package ru.shaxowskiy.javaspeakerclub.dto;

import java.util.UUID;

public record UserMediaUploadResponse(UUID id, String mediaMinioKey) {}
