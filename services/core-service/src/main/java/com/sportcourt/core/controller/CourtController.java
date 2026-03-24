package com.sportcourt.core.controller;

import com.sportcourt.core.api.ApiResponse;
import com.sportcourt.core.domain.Court;
import com.sportcourt.core.dto.CourtCreateRequest;
import com.sportcourt.core.dto.CourtResponse;
import com.sportcourt.core.mapper.CoreApiMapper;
import com.sportcourt.core.service.CourtService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/core/courts")
public class CourtController {

    private final CourtService courtService;

    public CourtController(CourtService courtService) {
        this.courtService = courtService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CourtResponse>> create(@Valid @RequestBody CourtCreateRequest req) {
        Court court = courtService.create(req);
        CourtResponse response = CoreApiMapper.toCourtResponse(court);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<CourtResponse>> list(@RequestParam(name = "venueId", required = false) UUID venueId) {
        List<CourtResponse> responses = courtService.list(venueId)
            .stream()
            .map(CoreApiMapper::toCourtResponse)
            .toList();
        return ApiResponse.success(responses);
    }
}
