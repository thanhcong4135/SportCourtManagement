package com.sportcourt.reporting.repository.projection;

import java.math.BigDecimal;

public interface BestHourProjection {
    Integer getHourOfDay();

    Long getBookingCount();

    BigDecimal getBookedHours();
}
