package com.sportcourt.auth.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AuthTokenResponse(
    UUID userId,
    String email,
    List<String> roles,
    String accessToken,
    OffsetDateTime accessTokenExpiresAt,
    String refreshToken,
    OffsetDateTime refreshTokenExpiresAt
) {
}
