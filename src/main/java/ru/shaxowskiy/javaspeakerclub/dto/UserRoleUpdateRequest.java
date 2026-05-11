package ru.shaxowskiy.javaspeakerclub.dto;

import ru.shaxowskiy.javaspeakerclub.security.AppRole;

public record UserRoleUpdateRequest(AppRole role) {
}
