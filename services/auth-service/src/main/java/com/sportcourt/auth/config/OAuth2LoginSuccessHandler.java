package com.sportcourt.auth.config;

import com.sportcourt.auth.dto.AuthTokenResponse;
import com.sportcourt.auth.service.AuthService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    private final String authorizedRedirectUri;

    public OAuth2LoginSuccessHandler(AuthService authService,
                                     @Value("${app.oauth2.authorized-redirect-uri}") String authorizedRedirectUri) {
        this.authService = authService;
        this.authorizedRedirectUri = authorizedRedirectUri;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User oauth2User = (OAuth2User) authentication.getPrincipal();
        AuthTokenResponse tokens = authService.loginWithGoogle(
            oauth2User.getAttribute("email"),
            oauth2User.getAttribute("name"),
            oauth2User.getAttribute("sub"),
            oauth2User.getAttribute("picture"),
            oauth2User.getAttribute("email_verified")
        );

        String redirectUrl = UriComponentsBuilder.fromUriString(authorizedRedirectUri)
            .queryParam("accessToken", tokens.accessToken())
            .queryParam("refreshToken", tokens.refreshToken())
            .queryParam("accessTokenExpiresAt", tokens.accessTokenExpiresAt())
            .queryParam("refreshTokenExpiresAt", tokens.refreshTokenExpiresAt())
            .queryParam("userId", tokens.userId())
            .queryParam("email", tokens.email())
            .queryParam("roles", String.join(",", tokens.roles()))
            .build()
            .encode()
            .toUriString();

        response.sendRedirect(redirectUrl);
    }
}
