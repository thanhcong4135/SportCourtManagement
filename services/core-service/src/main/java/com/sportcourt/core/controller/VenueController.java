package com.sportcourt.core.controller;

import com.sportcourt.core.api.ApiResponse;
import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.dto.VenueCreateRequest;
import com.sportcourt.core.dto.VenueImageCreateRequest;
import com.sportcourt.core.dto.VenueImageResponse;
import com.sportcourt.core.dto.VenueImageUpdateRequest;
import com.sportcourt.core.dto.VenueResponse;
import com.sportcourt.core.dto.VenueUpdateRequest;
import com.sportcourt.core.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

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
        VenueResponse response = venueService.toResponse(venue);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<VenueResponse>> list() {
        return ApiResponse.success(venueService.listResponses());
    }

    @PutMapping("/{id}")
    public ApiResponse<VenueResponse> update(
        @PathVariable UUID id,
        @Valid @RequestBody VenueUpdateRequest req
    ) {
        Venue venue = venueService.update(id, req);
        return ApiResponse.success(venueService.toResponse(venue));
    }

    @GetMapping("/{venueId}/images")
    public ApiResponse<List<VenueImageResponse>> listImages(@PathVariable UUID venueId) {
        return ApiResponse.success(venueService.listImages(venueId));
    }

    @PostMapping("/{venueId}/images")
    public ResponseEntity<ApiResponse<VenueImageResponse>> createImage(
        @PathVariable UUID venueId,
        @Valid @RequestBody VenueImageCreateRequest req
    ) {
        VenueImageResponse response = venueService.createImage(venueId, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PutMapping("/{venueId}/images/{imageId}")
    public ApiResponse<VenueImageResponse> updateImage(
        @PathVariable UUID venueId,
        @PathVariable UUID imageId,
        @Valid @RequestBody VenueImageUpdateRequest req
    ) {
        return ApiResponse.success(venueService.updateImage(venueId, imageId, req));
    }

    @DeleteMapping("/{venueId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable UUID venueId, @PathVariable UUID imageId) {
        venueService.deleteImage(venueId, imageId);
    }

    @PostMapping("/{venueId}/images/{imageId}/set-cover")
    public ApiResponse<VenueImageResponse> setCoverImage(
        @PathVariable UUID venueId,
        @PathVariable UUID imageId
    ) {
        return ApiResponse.success(venueService.setCoverImage(venueId, imageId));
    }
}
