package com.sportcourt.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
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
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(apiSecurityErrorHandler)
                .accessDeniedHandler(apiSecurityErrorHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/callback").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/payments/deposits/initiate").hasAnyRole("CUSTOMER", "OWNER", "ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/payments/**").hasAnyRole("CUSTOMER", "OWNER", "ADMIN", "STAFF", "SUPPORT")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            )
            .build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${app.security.jwt.secret}") String secret) {
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).build();
    }

    private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            List<SimpleGrantedAuthority> authorities = (roles == null ? Collections.<String>emptyList() : roles).stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }
}
