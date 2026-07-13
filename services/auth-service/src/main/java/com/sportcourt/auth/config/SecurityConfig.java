package com.sportcourt.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final ApiSecurityErrorHandler apiSecurityErrorHandler;
    private final ResourceLoader resourceLoader;

    public SecurityConfig(ApiSecurityErrorHandler apiSecurityErrorHandler,
                          ResourceLoader resourceLoader) {
        this.apiSecurityErrorHandler = apiSecurityErrorHandler;
        this.resourceLoader = resourceLoader;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler) throws Exception {
        JwtAuthenticationConverter jwtAuthConverter = new JwtAuthenticationConverter();
        jwtAuthConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<String> roles = jwt.getClaimAsStringList("roles");
            List<GrantedAuthority> authorities = (roles == null ? Collections.<String>emptyList() : roles)
                .stream()
                .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
            return authorities;
        });
        jwtAuthConverter.setPrincipalClaimName("sub");

        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(apiSecurityErrorHandler)
                .accessDeniedHandler(apiSecurityErrorHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/actuator/health",
                    "/actuator/info",
                    "/actuator/prometheus",
                    "/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html",
                    "/.well-known/jwks.json",
                    "/oauth2/**",
                    "/login/oauth2/**"
                ).permitAll()
                .requestMatchers(HttpMethod.GET, "/api/auth/oauth2/google").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/refresh", "/api/auth/logout")
                .permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2.successHandler(oAuth2LoginSuccessHandler))
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter)));

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtKeyMaterial jwtKeyMaterial(
        @Value("${app.security.jwt.signing-key.kid}") String signingKid,
        @Value("${app.security.jwt.signing-key.private-key-location}") String signingPrivateKeyLocation,
        @Value("${app.security.jwt.signing-key.public-key-location}") String signingPublicKeyLocation,
        @Value("${app.security.jwt.previous-key.kid:}") String previousKid,
        @Value("${app.security.jwt.previous-key.public-key-location:}") String previousPublicKeyLocation
    ) {
        try {
            RSAPrivateKey signingPrivateKey = parsePrivateKey(readResource(signingPrivateKeyLocation));
            RSAPublicKey signingPublicKey = parsePublicKey(readResource(signingPublicKeyLocation));

            RSAKey signingKey = new RSAKey.Builder(signingPublicKey)
                .privateKey(signingPrivateKey)
                .keyID(signingKid)
                .build();

            List<com.nimbusds.jose.jwk.JWK> publicVerificationKeys = new java.util.ArrayList<>();
            publicVerificationKeys.add(signingKey.toPublicJWK());

            RSAPublicKey previousPublicKey = null;

            if (!isBlank(previousKid) && !isBlank(previousPublicKeyLocation)) {
                previousPublicKey = parsePublicKey(readResource(previousPublicKeyLocation));
                RSAKey previousWithKid = new RSAKey.Builder(previousPublicKey)
                    .keyID(previousKid)
                    .build();
                publicVerificationKeys.add(previousWithKid);
            }

            return new JwtKeyMaterial(signingKey, new JWKSet(publicVerificationKeys), signingPublicKey, previousPublicKey);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build JWT key material", ex);
        }
    }

    @Bean
    JWKSet publicJwkSet(JwtKeyMaterial jwtKeyMaterial) {
        return jwtKeyMaterial.publicJwkSet();
    }

    @Bean
    JwtDecoder jwtDecoder(JwtKeyMaterial jwtKeyMaterial,
                          @Value("${app.security.jwt.issuer}") String issuer) {
        JwtDecoder primaryDecoder = buildDecoder(jwtKeyMaterial.signingPublicKey(), issuer);
        if (jwtKeyMaterial.previousPublicKey() == null) {
            return primaryDecoder;
        }

        JwtDecoder previousDecoder = buildDecoder(jwtKeyMaterial.previousPublicKey(), issuer);
        return token -> {
            try {
                return primaryDecoder.decode(token);
            } catch (Exception ex) {
                return previousDecoder.decode(token);
            }
        };
    }

    @Bean
    JwtEncoder jwtEncoder(JwtKeyMaterial jwtKeyMaterial) {
        JWKSet encoderSet = new JWKSet(jwtKeyMaterial.signingKey());
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(encoderSet));
    }

    private String readResource(String location) {
        try {
            Resource resource = resourceLoader.getResource(location);
            if (!resource.exists()) {
                throw new IllegalStateException("JWT key resource not found: " + location);
            }
            byte[] bytes = resource.getInputStream().readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read JWT key resource: " + location, ex);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private RSAPrivateKey parsePrivateKey(String pemContent) {
        try {
            byte[] der = parsePemBody(pemContent, "PRIVATE KEY");
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(der);
            return (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA private key", ex);
        }
    }

    private RSAPublicKey parsePublicKey(String pemContent) {
        try {
            byte[] der = parsePemBody(pemContent, "PUBLIC KEY");
            X509EncodedKeySpec spec = new X509EncodedKeySpec(der);
            return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid RSA public key", ex);
        }
    }

    private byte[] parsePemBody(String pemContent, String blockType) {
        String beginMarker = "-----BEGIN " + blockType + "-----";
        String endMarker = "-----END " + blockType + "-----";
        String normalized = pemContent
            .replace("\r", "")
            .replace(beginMarker, "")
            .replace(endMarker, "")
            .replace("\n", "")
            .trim();
        return Base64.getDecoder().decode(normalized);
    }

    private JwtDecoder buildDecoder(RSAPublicKey publicKey, String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefault(),
            JwtValidators.createDefaultWithIssuer(issuer)
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    public record JwtKeyMaterial(RSAKey signingKey,
                                 JWKSet publicJwkSet,
                                 RSAPublicKey signingPublicKey,
                                 RSAPublicKey previousPublicKey) {
    }
}
