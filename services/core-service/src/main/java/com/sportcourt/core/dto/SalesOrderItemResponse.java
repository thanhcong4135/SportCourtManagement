package com.sportcourt.core.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record SalesOrderItemResponse(
    UUID productId,
    String productName,
    int quantity,
    BigDecimal unitPrice,
    BigDecimal lineTotal
) {
}
