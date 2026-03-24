package com.sportcourt.auth.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(
    UUID userId,
    String email,
    String displayName,
    String status,
    List<String> roles
) {
}
