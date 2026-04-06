package com.sportcourt.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RevenueReportRowResponse(
    LocalDate reportDate,
    UUID venueId,
    BigDecimal bookingRevenue,
    BigDecimal depositRevenue,
    BigDecimal addOnRevenue,
    BigDecimal totalRevenue
) {
}
