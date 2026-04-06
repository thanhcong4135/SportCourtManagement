package com.sportcourt.reporting.controller;

import com.sportcourt.reporting.dto.BestHourReportRowResponse;
import com.sportcourt.reporting.dto.OccupancyReportRowResponse;
import com.sportcourt.reporting.dto.ReportPageResponse;
import com.sportcourt.reporting.dto.RevenueReportRowResponse;
import com.sportcourt.reporting.service.ReportService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
public class ReportingController {

    private final ReportService reportService;

    public ReportingController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/occupancy")
    public ReportPageResponse<OccupancyReportRowResponse> occupancy(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UUID venueId,
        @RequestParam(defaultValue = "reportDate,asc") String sort,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return reportService.occupancy(from, to, venueId, pageable, sort);
    }

    @GetMapping("/revenue")
    public ReportPageResponse<RevenueReportRowResponse> revenue(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UUID venueId,
        @RequestParam(defaultValue = "reportDate,asc") String sort,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return reportService.revenue(from, to, venueId, pageable, sort);
    }

    @GetMapping("/best-hours")
    public List<BestHourReportRowResponse> bestHours(
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(required = false) UUID venueId,
        @RequestParam(defaultValue = "5") Integer top
    ) {
        return reportService.bestHours(from, to, venueId, top);
    }

    @PostMapping("/projection/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetProjection() {
        reportService.resetProjections();
    }
}
