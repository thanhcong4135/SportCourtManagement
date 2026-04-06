package com.sportcourt.core.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PricingQuoteSlot(
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    UUID ruleId,
    String ruleName,
    BigDecimal pricePerHour,
    BigDecimal amount
) {
}
