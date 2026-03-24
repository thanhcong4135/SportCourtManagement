package com.sportcourt.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 128) String email,
    @NotBlank @Size(min = 8, max = 120) String password,
    @NotBlank @Size(max = 120) String displayName
) {
}
