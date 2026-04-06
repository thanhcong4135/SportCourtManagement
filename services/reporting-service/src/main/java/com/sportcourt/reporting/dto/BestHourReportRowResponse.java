package com.sportcourt.reporting.dto;

import java.math.BigDecimal;

public record BestHourReportRowResponse(
    int hourOfDay,
    long bookingCount,
    BigDecimal bookedHours
) {
}
