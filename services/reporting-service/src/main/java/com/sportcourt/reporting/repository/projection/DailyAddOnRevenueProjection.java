package com.sportcourt.reporting.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyAddOnRevenueProjection {
    LocalDate getReportDate();

    Object getVenueId();

    BigDecimal getAddOnRevenue();
}
