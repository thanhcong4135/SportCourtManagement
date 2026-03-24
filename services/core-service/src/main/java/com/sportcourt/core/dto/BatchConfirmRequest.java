package com.sportcourt.core.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BatchConfirmRequest(
    @NotEmpty List<@NotNull UUID> bookingIds
) {
}
