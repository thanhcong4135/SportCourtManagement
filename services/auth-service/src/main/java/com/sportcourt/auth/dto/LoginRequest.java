package com.sportcourt.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Email @Size(max = 128) String email,
    @NotBlank @Size(max = 120) String password
) {
}
