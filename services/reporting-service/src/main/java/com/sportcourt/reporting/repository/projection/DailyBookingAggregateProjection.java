package com.sportcourt.reporting.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyBookingAggregateProjection {
    LocalDate getReportDate();

    Object getVenueId();

    Long getTotalBookings();

    BigDecimal getBookedHours();
}
