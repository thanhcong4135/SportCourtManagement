package com.sportcourt.auth.dto;

import java.util.UUID;

public record TokenRevokeResponse(
    UUID userId,
    int revokedCount
) {
}
