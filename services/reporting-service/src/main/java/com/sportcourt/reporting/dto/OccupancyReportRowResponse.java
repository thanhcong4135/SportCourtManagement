package com.sportcourt.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record OccupancyReportRowResponse(
    LocalDate reportDate,
    UUID venueId,
    long totalBookings,
    BigDecimal bookedHours
) {
}
