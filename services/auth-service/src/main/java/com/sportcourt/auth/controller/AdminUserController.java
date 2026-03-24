package com.sportcourt.auth.controller;

import com.sportcourt.auth.dto.AdminUserResponse;
import com.sportcourt.auth.dto.TokenRevokeResponse;
import com.sportcourt.auth.dto.UpdateUserRolesRequest;
import com.sportcourt.auth.dto.UpdateUserStatusRequest;
import com.sportcourt.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/auth/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @PutMapping("/{userId}/roles")
    public AdminUserResponse updateRoles(@PathVariable UUID userId,
                                         @Valid @RequestBody UpdateUserRolesRequest request) {
        return authService.updateUserRoles(userId, request);
    }

    @PutMapping("/{userId}/status")
    public AdminUserResponse updateStatus(@PathVariable UUID userId,
                                          @Valid @RequestBody UpdateUserStatusRequest request) {
        return authService.updateUserStatus(userId, request);
    }

    @PostMapping("/{userId}/revoke-tokens")
    public TokenRevokeResponse revokeAllTokens(@PathVariable UUID userId) {
        return authService.logoutAll(userId);
    }
}
