package com.sportcourt.auth.service;

import com.sportcourt.auth.domain.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final long accessTokenTtlSeconds;
    private final String issuer;
    private final String signingKid;

    public JwtTokenService(JwtEncoder jwtEncoder,
                           @Value("${app.security.jwt.access-token-ttl-seconds:3600}") long accessTokenTtlSeconds,
                           @Value("${app.security.jwt.issuer:https://auth.sportcourt.local}") String issuer,
                           @Value("${app.security.jwt.signing-key.kid:sc-auth-rs256-v1}") String signingKid) {
        this.jwtEncoder = jwtEncoder;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.issuer = issuer;
        this.signingKid = signingKid;
    }

    public TokenIssueResult issueAccessToken(UserAccount user) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plusSeconds(accessTokenTtlSeconds);
        List<String> roles = user.getRoles().stream()
            .map(role -> role.getName().name().replace("ROLE_", ""))
            .sorted()
            .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .issuedAt(now.toInstant())
            .expiresAt(expiresAt.toInstant())
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("roles", roles)
            .build();

        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
            .keyId(signingKid)
            .build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenIssueResult(tokenValue, expiresAt, roles);
    }

    public record TokenIssueResult(String accessToken, OffsetDateTime expiresAt, List<String> roles) {
    }
}
