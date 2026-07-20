package com.sportcourt.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;

public record VenueCreateRequest(
    @NotBlank @Size(max = 255) String name,
    @NotBlank @Size(max = 500) String address,
    String description,
    @Size(max = 1024) String coverImageUrl,
    @Size(max = 1024) String imageUrl,
    @Size(max = 50) String phone,
    LocalTime openingTime,
    LocalTime closingTime,
    BigDecimal latitude,
    BigDecimal longitude
) {
    public VenueCreateRequest(String name, String address) {
        this(name, address, null, null, null, null, null, null, null, null);
    }
}
