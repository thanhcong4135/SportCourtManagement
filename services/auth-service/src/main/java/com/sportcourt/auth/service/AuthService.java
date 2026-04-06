package com.sportcourt.auth.service;

import com.sportcourt.auth.domain.RefreshToken;
import com.sportcourt.auth.domain.Role;
import com.sportcourt.auth.domain.UserAccount;
import com.sportcourt.auth.domain.enums.RoleName;
import com.sportcourt.auth.domain.enums.UserStatus;
import com.sportcourt.auth.dto.AdminUserResponse;
import com.sportcourt.auth.dto.AuthTokenResponse;
import com.sportcourt.auth.dto.LoginRequest;
import com.sportcourt.auth.dto.MeResponse;
import com.sportcourt.auth.dto.RefreshTokenRequest;
import com.sportcourt.auth.dto.RegisterRequest;
import com.sportcourt.auth.dto.TokenRevokeResponse;
import com.sportcourt.auth.dto.UpdateUserRolesRequest;
import com.sportcourt.auth.dto.UpdateUserStatusRequest;
import com.sportcourt.auth.repository.RefreshTokenRepository;
import com.sportcourt.auth.repository.RoleRepository;
import com.sportcourt.auth.repository.UserAccountRepository;
import jakarta.persistence.criteria.JoinType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final long refreshTokenTtlSeconds;
    private final boolean revokeAllOnRefreshTokenReuse;

    public AuthService(UserAccountRepository userAccountRepository,
                       RoleRepository roleRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService,
                       @Value("${app.security.jwt.refresh-token-ttl-seconds:2592000}") long refreshTokenTtlSeconds,
                       @Value("${app.security.jwt.refresh-token-reuse-revoke-all:true}")
                       boolean revokeAllOnRefreshTokenReuse) {
        this.userAccountRepository = userAccountRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
        this.revokeAllOnRefreshTokenReuse = revokeAllOnRefreshTokenReuse;
    }

    @Transactional
    public AuthTokenResponse register(RegisterRequest request) {
        if (userAccountRepository.existsByEmailIgnoreCase(request.email().trim())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        Role customerRole = roleRepository.findByName(RoleName.ROLE_CUSTOMER)
            .orElseThrow(() -> new IllegalStateException("Missing default role ROLE_CUSTOMER"));

        UserAccount user = new UserAccount();
        user.setEmail(request.email().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        user.setRoles(Set.of(customerRole));
        UserAccount saved = userAccountRepository.save(user);

        return issueTokens(saved);
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByEmailIgnoreCase(request.email().trim())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }

        return issueTokens(user);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        String tokenHash = sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (stored.getRevokedAt() != null) {
            if (revokeAllOnRefreshTokenReuse) {
                refreshTokenRepository.revokeAllActiveByUserId(stored.getUser().getId(), now);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token reuse detected");
        }
        if (stored.getExpiresAt().isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token expired or revoked");
        }

        stored.setRevokedAt(now);
        refreshTokenRepository.save(stored);
        return issueTokens(stored.getUser());
    }

    @Transactional
    public void logout(RefreshTokenRequest request) {
        String tokenHash = sha256(request.refreshToken());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(OffsetDateTime.now(ZoneOffset.UTC));
                refreshTokenRepository.save(token);
            }
        });
    }

    @Transactional
    public TokenRevokeResponse logoutAll(UUID userId) {
        userAccountRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        int revoked = refreshTokenRepository.revokeAllActiveByUserId(userId, OffsetDateTime.now(ZoneOffset.UTC));
        return new TokenRevokeResponse(userId, revoked);
    }

    @Transactional
    public TokenRevokeResponse logoutAllBySubject(String subject) {
        return logoutAll(parseUserId(subject));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID userId) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toAdminUserResponse(user);
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(String query, UserStatus status, RoleName role, Pageable pageable) {
        Specification<UserAccount> spec = Specification.where(null);

        if (query != null && !query.trim().isEmpty()) {
            String likeQuery = "%" + query.trim().toLowerCase() + "%";
            spec = spec.and((root, cq, cb) -> cb.or(
                cb.like(cb.lower(root.get("email")), likeQuery),
                cb.like(cb.lower(root.get("displayName")), likeQuery)
            ));
        }

        if (status != null) {
            spec = spec.and((root, cq, cb) -> cb.equal(root.get("status"), status));
        }

        if (role != null) {
            spec = spec.and((root, cq, cb) -> {
                cq.distinct(true);
                return cb.equal(root.join("roles", JoinType.LEFT).get("name"), role);
            });
        }

        return userAccountRepository.findAll(spec, pageable).map(this::toAdminUserResponse);
    }

    @Transactional
    public AdminUserResponse updateUserRoles(UUID userId, UpdateUserRolesRequest request) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Set<Role> roles = request.roles().stream()
            .filter(Objects::nonNull)
            .map(this::findRoleOrThrow)
            .collect(java.util.stream.Collectors.toSet());
        if (roles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one role is required");
        }

        user.setRoles(roles);
        UserAccount saved = userAccountRepository.save(user);
        return toAdminUserResponse(saved);
    }

    @Transactional
    public AdminUserResponse updateUserStatus(UUID userId, UpdateUserStatusRequest request) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setStatus(request.status());
        UserAccount saved = userAccountRepository.save(user);
        return toAdminUserResponse(saved);
    }

    @Transactional(readOnly = true)
    public MeResponse me(Jwt jwt) {
        UUID userId = parseUserId(jwt.getSubject());
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<String> roles = user.getRoles().stream()
            .map(role -> role.getName().name())
            .sorted()
            .toList();
        return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getStatus().name(), roles);
    }

    private AuthTokenResponse issueTokens(UserAccount user) {
        JwtTokenService.TokenIssueResult access = jwtTokenService.issueAccessToken(user);
        String refreshToken = UUID.randomUUID() + "." + UUID.randomUUID();
        OffsetDateTime refreshExpiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(refreshTokenTtlSeconds);

        RefreshToken entity = new RefreshToken();
        entity.setUser(user);
        entity.setTokenHash(sha256(refreshToken));
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setExpiresAt(refreshExpiresAt);
        refreshTokenRepository.save(entity);

        refreshTokenRepository.deleteByUser_IdAndRevokedAtIsNullAndExpiresAtBefore(user.getId(), OffsetDateTime.now(ZoneOffset.UTC));

        return new AuthTokenResponse(
            user.getId(),
            user.getEmail(),
            access.roles(),
            access.accessToken(),
            access.expiresAt(),
            refreshToken,
            refreshExpiresAt
        );
    }

    private UUID parseUserId(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private Role findRoleOrThrow(RoleName roleName) {
        return roleRepository.findByName(roleName)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Role does not exist: " + roleName.name()
            ));
    }

    private AdminUserResponse toAdminUserResponse(UserAccount user) {
        List<String> roles = user.getRoles().stream()
            .map(role -> role.getName().name())
            .sorted()
            .toList();
        return new AdminUserResponse(
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getStatus().name(),
            roles
        );
    }
}
