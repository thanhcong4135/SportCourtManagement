package com.sportcourt.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class SecurityConfig {

    private final ApiSecurityErrorHandler apiSecurityErrorHandler;

    public SecurityConfig(ApiSecurityErrorHandler apiSecurityErrorHandler) {
        this.apiSecurityErrorHandler = apiSecurityErrorHandler;
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(apiSecurityErrorHandler)
                .accessDeniedHandler(apiSecurityErrorHandler)
            )
            .authorizeExchange(exchanges -> exchanges
                .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                .permitAll()
                .pathMatchers(HttpMethod.GET, "/api/core/venues/**", "/api/core/courts/**", "/api/core/availability/**")
                .permitAll()
                .pathMatchers(HttpMethod.POST, "/api/payments/callback").permitAll()
                .pathMatchers(HttpMethod.POST, "/api/core/venues/**", "/api/core/courts/**")
                .hasAnyRole("ADMIN", "OWNER")
                .pathMatchers("/api/core/ops/**", "/api/payments/ops/**")
                .hasRole("ADMIN")
                .pathMatchers("/api/core/bookings/**")
                .hasAnyRole("CUSTOMER", "OWNER", "ADMIN")
                .pathMatchers(HttpMethod.POST, "/api/payments/deposits/initiate")
                .hasAnyRole("CUSTOMER", "OWNER", "ADMIN")
                .pathMatchers(HttpMethod.GET, "/api/payments/**")
                .hasAnyRole("CUSTOMER", "OWNER", "ADMIN", "STAFF", "SUPPORT")
                .pathMatchers("/api/notifications/**")
                .hasAnyRole("ADMIN", "OWNER", "STAFF", "SUPPORT")
                .pathMatchers("/api/reports/**")
                .hasAnyRole("ADMIN", "OWNER", "STAFF", "SUPPORT")
                .pathMatchers("/api/chatbot/**")
                .hasAnyRole("CUSTOMER", "OWNER", "STAFF", "ADMIN", "SUPPORT")
                .anyExchange().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .build();
    }

    @Bean
    ReactiveJwtDecoder jwtDecoder(@Value("${app.security.jwt.jwk-set-uri}") String jwkSetUri,
                                  @Value("${app.security.jwt.issuer-uri}") String issuerUri) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(),
            JwtValidators.createDefaultWithIssuer(issuerUri)
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> jwtAuthenticationConverter() {
        return jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            List<SimpleGrantedAuthority> authorities = (roles == null ? Collections.<String>emptyList() : roles).stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
            return Mono.just(new JwtAuthenticationToken(jwt, authorities, jwt.getSubject()));
        };
    }
}
