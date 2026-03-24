package com.sportcourt.core.controller;

import com.sportcourt.core.api.ApiResponse;
import com.sportcourt.core.dto.AvailabilityResponse;
import com.sportcourt.core.service.BookingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/core/availability")
public class AvailabilityController {

    private final BookingService bookingService;

    public AvailabilityController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ApiResponse<AvailabilityResponse> check(@RequestParam UUID courtId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end) {
        boolean available = bookingService.isAvailable(courtId, start, end);
        return ApiResponse.success(new AvailabilityResponse(available));
    }
}
