package com.sportcourt.core.dto;

import com.sportcourt.core.domain.enums.CustomerTier;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PricingQuoteResponse(
    UUID courtId,
    CustomerTier customerTier,
    OffsetDateTime startTime,
    OffsetDateTime endTime,
    BigDecimal totalPrice,
    List<PricingQuoteSlotResponse> slots
) {
}
