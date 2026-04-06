package com.sportcourt.core.dto;

import com.sportcourt.core.domain.enums.PricingDayType;
import com.sportcourt.core.domain.enums.PricingRuleCustomerTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

public record PricingRuleCreateRequest(
    @NotNull UUID courtId,
    @NotBlank String name,
    @NotNull PricingDayType dayType,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime,
    @NotNull PricingRuleCustomerTier customerTier,
    @NotNull @Positive BigDecimal pricePerHour,
    Integer priority
) {
}
