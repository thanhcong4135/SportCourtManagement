package com.sportcourt.auth.dto;

import com.sportcourt.auth.domain.enums.RoleName;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateUserRolesRequest(
    @NotEmpty List<@NotNull RoleName> roles
) {
}
