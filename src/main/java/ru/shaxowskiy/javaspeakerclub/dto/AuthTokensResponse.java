package ru.shaxowskiy.javaspeakerclub.dto;

public record AuthTokensResponse(
        String accessToken,
        long accessTokenExpiresIn,
        String refreshToken,
        long refreshTokenExpiresIn,
        AuthResponse user
) {
}
