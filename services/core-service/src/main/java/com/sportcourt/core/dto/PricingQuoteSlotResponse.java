package com.sportcourt.core.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PricingQuoteSlotResponse(
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    UUID ruleId,
    String ruleName,
    BigDecimal pricePerHour,
    BigDecimal amount
) {
}
