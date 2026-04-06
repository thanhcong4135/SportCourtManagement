package com.sportcourt.auth.controller;

import com.sportcourt.auth.domain.enums.RoleName;
import com.sportcourt.auth.domain.enums.UserStatus;
import com.sportcourt.auth.dto.AdminUserResponse;
import com.sportcourt.auth.dto.TokenRevokeResponse;
import com.sportcourt.auth.dto.UpdateUserRolesRequest;
import com.sportcourt.auth.dto.UpdateUserStatusRequest;
import com.sportcourt.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping
    public Page<AdminUserResponse> list(@RequestParam(required = false) String q,
                                        @RequestParam(required = false) UserStatus status,
                                        @RequestParam(required = false) RoleName role,
                                        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                        Pageable pageable) {
        return authService.listUsers(q, status, role, pageable);
    }

    @GetMapping("/{userId}")
    public AdminUserResponse getById(@PathVariable UUID userId) {
        return authService.getUser(userId);
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
