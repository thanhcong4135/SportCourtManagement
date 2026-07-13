package com.sportcourt.auth.controller;

import com.sportcourt.auth.dto.AuthTokenResponse;
import com.sportcourt.auth.dto.LoginRequest;
import com.sportcourt.auth.dto.MeResponse;
import com.sportcourt.auth.dto.RefreshTokenRequest;
import com.sportcourt.auth.dto.RegisterRequest;
import com.sportcourt.auth.dto.TokenRevokeResponse;
import com.sportcourt.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthTokenResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthTokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/oauth2/google")
    public ResponseEntity<Void> googleLogin() {
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/oauth2/authorization/google")
            .build();
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }

    @PostMapping("/logout-all")
    public TokenRevokeResponse logoutAll(JwtAuthenticationToken authenticationToken) {
        Jwt jwt = authenticationToken.getToken();
        return authService.logoutAllBySubject(jwt.getSubject());
    }

    @GetMapping("/me")
    public MeResponse me(JwtAuthenticationToken authenticationToken) {
        Jwt jwt = authenticationToken.getToken();
        return authService.me(jwt);
    }
}
