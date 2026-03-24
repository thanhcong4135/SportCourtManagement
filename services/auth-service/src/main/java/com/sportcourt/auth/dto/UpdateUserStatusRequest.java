package com.sportcourt.auth.dto;

import com.sportcourt.auth.domain.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
    @NotNull UserStatus status
) {
}
