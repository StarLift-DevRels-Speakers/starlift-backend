package ru.shaxowskiy.javaspeakerclub.dto;

import java.util.List;

public record UserRoleUpdateResponse(Long userId, List<String> roles) {
}
