package com.sportcourt.core.dto;

import com.sportcourt.core.domain.enums.PricingDayType;
import com.sportcourt.core.domain.enums.PricingRuleCustomerTier;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PricingRuleResponse(
    UUID id,
    UUID courtId,
    String name,
    PricingDayType dayType,
    LocalTime startTime,
    LocalTime endTime,
    PricingRuleCustomerTier customerTier,
    BigDecimal pricePerHour,
    int priority,
    boolean active,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
