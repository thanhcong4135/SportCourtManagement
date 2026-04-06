package com.sportcourt.auth.service;

import com.sportcourt.auth.domain.Role;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class AuthServiceIntegrationTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userAccountRepository.deleteAll();
        roleRepository.deleteAll();
        seedRoles();
    }

    @Test
    void register_shouldCreateCustomerAndIssueTokens() {
        AuthTokenResponse response = authService.register(new RegisterRequest(
            "customer@test.com",
            "strongPass123",
            "Customer Test"
        ));

        assertThat(response.userId()).isNotNull();
        assertThat(response.roles()).containsExactly("CUSTOMER");
        assertThat(response.accessToken()).isNotBlank();
        assertThat(response.refreshToken()).isNotBlank();

        Jwt jwt = jwtDecoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(response.userId().toString());
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");
    }

    @Test
    void login_shouldRejectWrongPassword() {
        authService.register(new RegisterRequest("u1@test.com", "strongPass123", "U1"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("u1@test.com", "wrong")))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void refresh_shouldRotateRefreshToken() {
        AuthTokenResponse first = authService.register(new RegisterRequest("u2@test.com", "strongPass123", "U2"));
        AuthTokenResponse refreshed = authService.refresh(new RefreshTokenRequest(first.refreshToken()));

        assertThat(refreshed.userId()).isEqualTo(first.userId());
        assertThat(refreshed.refreshToken()).isNotEqualTo(first.refreshToken());

        Jwt refreshedJwt = jwtDecoder.decode(refreshed.accessToken());
        assertThat(refreshedJwt.getSubject()).isEqualTo(first.userId().toString());
        assertThat(refreshedJwt.getClaimAsStringList("roles")).containsExactly("CUSTOMER");

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(first.refreshToken())))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void me_shouldReturnCurrentUserProfile() {
        AuthTokenResponse token = authService.register(new RegisterRequest("u3@test.com", "strongPass123", "U3"));
        Jwt jwt = jwtDecoder.decode(token.accessToken());

        MeResponse me = authService.me(jwt);

        assertThat(me.userId()).isEqualTo(token.userId());
        assertThat(me.email()).isEqualTo("u3@test.com");
        assertThat(me.roles()).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void logout_shouldRevokeRefreshToken() {
        AuthTokenResponse token = authService.register(new RegisterRequest("u4@test.com", "strongPass123", "U4"));

        authService.logout(new RefreshTokenRequest(token.refreshToken()));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(token.refreshToken())))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        authService.logout(new RefreshTokenRequest(token.refreshToken()));
    }

    @Test
    void refreshTokenReuse_shouldRevokeAllUserActiveTokens() {
        AuthTokenResponse first = authService.register(new RegisterRequest("u5@test.com", "strongPass123", "U5"));
        AuthTokenResponse rotated = authService.refresh(new RefreshTokenRequest(first.refreshToken()));

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(first.refreshToken())))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest(rotated.refreshToken())))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void admin_shouldUpdateRolesAndStatus() {
        AuthTokenResponse token = authService.register(new RegisterRequest("u6@test.com", "strongPass123", "U6"));

        var updatedRoles = authService.updateUserRoles(
            token.userId(),
            new UpdateUserRolesRequest(java.util.List.of(RoleName.ROLE_OWNER, RoleName.ROLE_STAFF))
        );
        assertThat(updatedRoles.roles()).containsExactly("ROLE_OWNER", "ROLE_STAFF");

        var updatedStatus = authService.updateUserStatus(
            token.userId(),
            new UpdateUserStatusRequest(UserStatus.LOCKED)
        );
        assertThat(updatedStatus.status()).isEqualTo("LOCKED");
    }

    @Test
    void logoutAll_shouldRevokeAllActiveTokensForUser() {
        AuthTokenResponse token = authService.register(new RegisterRequest("u7@test.com", "strongPass123", "U7"));
        authService.refresh(new RefreshTokenRequest(token.refreshToken()));

        TokenRevokeResponse result = authService.logoutAll(token.userId());
        assertThat(result.revokedCount()).isGreaterThan(0);
    }

    @Test
    void admin_shouldGetUserById() {
        AuthTokenResponse token = authService.register(new RegisterRequest("u8@test.com", "strongPass123", "U8"));

        AdminUserResponse user = authService.getUser(token.userId());

        assertThat(user.userId()).isEqualTo(token.userId());
        assertThat(user.email()).isEqualTo("u8@test.com");
        assertThat(user.roles()).contains("ROLE_CUSTOMER");
    }

    @Test
    void admin_shouldListUsersWithFilters() {
        AuthTokenResponse u9 = authService.register(new RegisterRequest("owner9@test.com", "strongPass123", "Owner Nine"));
        authService.register(new RegisterRequest("customer10@test.com", "strongPass123", "Customer Ten"));

        authService.updateUserRoles(
            u9.userId(),
            new UpdateUserRolesRequest(java.util.List.of(RoleName.ROLE_OWNER, RoleName.ROLE_STAFF))
        );

        var ownerPage = authService.listUsers("owner9", null, RoleName.ROLE_OWNER, PageRequest.of(0, 10));

        assertThat(ownerPage.getTotalElements()).isEqualTo(1);
        assertThat(ownerPage.getContent().get(0).email()).isEqualTo("owner9@test.com");
    }

    private void seedRoles() {
        for (RoleName roleName : RoleName.values()) {
            Role role = new Role();
            role.setName(roleName);
            roleRepository.save(role);
        }
    }
}
