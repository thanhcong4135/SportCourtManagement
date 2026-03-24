package com.sportcourt.reporting.service;

import com.sportcourt.reporting.dto.DailyBookingReportResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class ReportService {

    public DailyBookingReportResponse getDailyBookingReport(LocalDate date) {
        return new DailyBookingReportResponse(
            date,
            0L,
            0L,
            0L,
            BigDecimal.ZERO
        );
    }
}
