package com.sportcourt.core.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BatchDepositRequest(
    @NotEmpty List<@Valid BatchDepositItemRequest> items
) {
}
