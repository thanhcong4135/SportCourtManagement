package com.sportcourt.core.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SalesOrderItemRequest(
    @NotNull UUID productId,
    @Min(1) int quantity
) {
}
