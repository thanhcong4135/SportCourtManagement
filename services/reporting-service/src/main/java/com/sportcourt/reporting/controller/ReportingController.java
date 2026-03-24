package com.sportcourt.reporting.controller;

import com.sportcourt.reporting.dto.DailyBookingReportResponse;
import com.sportcourt.reporting.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports")
public class ReportingController {

    private final ReportService reportService;

    public ReportingController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/bookings/daily")
    public DailyBookingReportResponse getDailyBookingReport(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate date
    ) {
        LocalDate effectiveDate = date != null ? date : LocalDate.now();
        return reportService.getDailyBookingReport(effectiveDate);
    }
}
