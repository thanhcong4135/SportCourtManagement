package com.sportcourt.core.controller;

import com.sportcourt.core.api.ApiResponse;
import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.dto.VenueCreateRequest;
import com.sportcourt.core.dto.VenueResponse;
import com.sportcourt.core.mapper.CoreApiMapper;
import com.sportcourt.core.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/core/venues")
public class VenueController {

    private final VenueService venueService;

    public VenueController(VenueService venueService) {
        this.venueService = venueService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VenueResponse>> create(@Valid @RequestBody VenueCreateRequest req) {
        Venue venue = venueService.create(req);
        VenueResponse response = CoreApiMapper.toVenueResponse(venue);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<VenueResponse>> list() {
        List<VenueResponse> responses = venueService.list()
            .stream()
            .map(CoreApiMapper::toVenueResponse)
            .toList();
        return ApiResponse.success(responses);
    }
}
