package com.sportcourt.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyBookingReportResponse(
    LocalDate date,
    long totalBookings,
    long confirmedBookings,
    long canceledBookings,
    BigDecimal totalRevenue
) {
}
