package com.sportcourt.core.service;

import com.sportcourt.core.domain.enums.CustomerTier;

import java.math.BigDecimal;
import java.util.List;

public record PricingQuoteResult(
    CustomerTier customerTier,
    BigDecimal totalPrice,
    List<PricingQuoteSlot> slots,
    String snapshotJson
) {
}
