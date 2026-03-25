package com.sportcourt.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class GatewaySecurityIntegrationTest {

    private static final String JWT_ISSUER = "https://auth.sportcourt.local";
    private static final String JWT_KID = "gateway-test-rs256-kid";
    private static final AtomicInteger CORE_REQUEST_COUNT = new AtomicInteger();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<UUID, BookingStub> BOOKINGS = new ConcurrentHashMap<>();
    private static RSAKey gatewayTestRsaKey;

    private static MockWebServer authService;
    private static MockWebServer coreService;

    @Autowired
    private WebTestClient webTestClient;

    @BeforeAll
    static void setUpServers() throws Exception {
        gatewayTestRsaKey = new RSAKeyGenerator(2048)
            .keyID(JWT_KID)
            .generate();

        authService = new MockWebServer();
        coreService = new MockWebServer();

        authService.setDispatcher(new AuthDispatcher());
        coreService.setDispatcher(new CoreDispatcher());

        authService.start();
        coreService.start();
    }

    @AfterAll
    static void tearDownServers() throws Exception {
        authService.shutdown();
        coreService.shutdown();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.downstream.auth-service-url", () -> authService.url("/").toString().replaceAll("/$", ""));
        registry.add("app.downstream.core-service-url", () -> coreService.url("/").toString().replaceAll("/$", ""));
        registry.add("app.security.jwt.issuer-uri", () -> JWT_ISSUER);
        registry.add("app.security.jwt.jwk-set-uri", () -> authService.url("/.well-known/jwks.json").toString());
    }

    @Test
    void loginThenCallCore_shouldRespectCustomerRole() throws Exception {
        CORE_REQUEST_COUNT.set(0);
        BOOKINGS.clear();

        EntityExchangeResult<AuthTokenStub> loginResult = webTestClient.post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "email": "customer@test.com",
                  "password": "strongPass123"
                }
                """)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AuthTokenStub.class)
            .returnResult();

        String accessToken = loginResult.getResponseBody().accessToken();

        webTestClient.get()
            .uri("/api/core/bookings/" + UUID.randomUUID())
            .headers(headers -> headers.setBearerAuth(accessToken))
            .exchange()
            .expectStatus().isOk();

        RecordedRequest forwarded = coreService.takeRequest(1, TimeUnit.SECONDS);
        assertThat(forwarded).isNotNull();
        assertThat(forwarded.getHeader("Authorization")).isEqualTo("Bearer " + accessToken);
        assertThat(CORE_REQUEST_COUNT.get()).isEqualTo(1);

        webTestClient.post()
            .uri("/api/core/venues")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "name": "My Venue",
                  "address": "HCM"
                }
                """)
            .exchange()
            .expectStatus().isForbidden()
            .expectBody()
            .jsonPath("$.code").isEqualTo("FORBIDDEN")
            .jsonPath("$.traceId").isNotEmpty();

        assertThat(CORE_REQUEST_COUNT.get()).isEqualTo(1);
    }

    @Test
    void anonymousCall_shouldReturnStandardUnauthorizedError() {
        webTestClient.get()
            .uri("/api/core/bookings")
            .exchange()
            .expectStatus().isUnauthorized()
            .expectBody()
            .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
            .jsonPath("$.message").isEqualTo("Authentication required")
            .jsonPath("$.traceId").isNotEmpty()
            .jsonPath("$.timestamp").isNotEmpty()
            .jsonPath("$.path").isEqualTo("/api/core/bookings");
    }

    @Test
    void loginThenCallCore_shouldAllowOwnerRole() {
        CORE_REQUEST_COUNT.set(0);
        BOOKINGS.clear();

        EntityExchangeResult<AuthTokenStub> loginResult = webTestClient.post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "email": "owner@test.com",
                  "password": "strongPass123"
                }
                """)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AuthTokenStub.class)
            .returnResult();

        String accessToken = loginResult.getResponseBody().accessToken();

        webTestClient.post()
            .uri("/api/core/venues")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "name": "Owner Venue",
                  "address": "HCM"
                }
                """)
            .exchange()
            .expectStatus().isCreated();

        assertThat(CORE_REQUEST_COUNT.get()).isEqualTo(1);
    }

    @Test
    void bookingFlowThroughGateway_shouldDraftDepositConfirmAndBlockAvailability() throws Exception {
        BOOKINGS.clear();

        EntityExchangeResult<AuthTokenStub> loginResult = webTestClient.post()
            .uri("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "email": "customer@test.com",
                  "password": "strongPass123"
                }
                """)
            .exchange()
            .expectStatus().isOk()
            .expectBody(AuthTokenStub.class)
            .returnResult();

        String accessToken = loginResult.getResponseBody().accessToken();
        UUID customerId = loginResult.getResponseBody().userId();
        UUID courtId = UUID.fromString("7470dc29-5680-4834-bf0a-e21a65d3f13c");
        String start = "2026-03-20T08:00:00+07:00";
        String end = "2026-03-20T10:00:00+07:00";

        String draftBody = webTestClient.post()
            .uri("/api/core/bookings/draft")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "courtId": "%s",
                  "customerId": "%s",
                  "startTime": "%s",
                  "endTime": "%s",
                  "priceTotal": 400000.00
                }
                """.formatted(courtId, customerId, start, end))
            .exchange()
            .expectStatus().isCreated()
            .expectBody(String.class)
            .returnResult()
            .getResponseBody();

        UUID bookingId = UUID.fromString(MAPPER.readTree(draftBody).path("data").path("id").asText());

        webTestClient.post()
            .uri("/api/core/bookings/" + bookingId + "/deposit")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""
                {
                  "amount": 200000.00
                }
                """)
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data.paymentStatus").isEqualTo("DEPOSITED");

        webTestClient.post()
            .uri("/api/core/bookings/" + bookingId + "/confirm")
            .headers(headers -> headers.setBearerAuth(accessToken))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data.status").isEqualTo("CONFIRMED")
            .jsonPath("$.data.paymentStatus").isEqualTo("DEPOSITED");

        webTestClient.get()
            .uri(uriBuilder -> uriBuilder.path("/api/core/availability")
                .queryParam("courtId", courtId)
                .queryParam("start", start)
                .queryParam("end", end)
                .build())
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.data.available").isEqualTo(false);
    }

    private record AuthTokenStub(UUID userId,
                                 String email,
                                 List<String> roles,
                                 String accessToken,
                                 OffsetDateTime accessTokenExpiresAt,
                                 String refreshToken,
                                 OffsetDateTime refreshTokenExpiresAt) {
    }

    private static final class AuthDispatcher extends Dispatcher {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            if ("GET".equals(request.getMethod()) && "/.well-known/jwks.json".equals(request.getPath())) {
                try {
                    String jwksPayload = MAPPER.writeValueAsString(
                        Map.of("keys", List.of(gatewayTestRsaKey.toPublicJWK().toJSONObject()))
                    );
                    return new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Content-Type", "application/json")
                        .setBody(jwksPayload);
                } catch (Exception ex) {
                    return new MockResponse().setResponseCode(500);
                }
            }

            if ("POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getPath())) {
                String body = request.getBody().readUtf8();
                boolean owner = body.contains("owner@test.com");

                UUID userId = owner
                    ? UUID.fromString("22222222-2222-2222-2222-222222222222")
                    : UUID.fromString("11111111-1111-1111-1111-111111111111");
                String email = owner ? "owner@test.com" : "customer@test.com";
                List<String> roles = owner ? List.of("OWNER") : List.of("CUSTOMER");

                String token;
                try {
                    token = issueToken(userId, email, roles);
                } catch (JOSEException e) {
                    return new MockResponse().setResponseCode(500);
                }

                String responseBody = """
                    {
                      "userId": "%s",
                      "email": "%s",
                      "roles": %s,
                      "accessToken": "%s",
                      "accessTokenExpiresAt": "%s",
                      "refreshToken": "mock-refresh-token",
                      "refreshTokenExpiresAt": "%s"
                    }
                    """.formatted(
                    userId,
                    email,
                    toJsonArray(roles),
                    token,
                    OffsetDateTime.now(ZoneOffset.UTC).plusHours(1),
                    OffsetDateTime.now(ZoneOffset.UTC).plusDays(30)
                );

                return new MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody(responseBody);
            }
            return new MockResponse().setResponseCode(404);
        }
    }

    private static final class CoreDispatcher extends Dispatcher {
        @Override
        public MockResponse dispatch(RecordedRequest request) {
            String fullPath = request.getPath();
            String path = pathWithoutQuery(fullPath);
            if ("GET".equals(request.getMethod()) && path != null && path.startsWith("/api/core/bookings/")) {
                CORE_REQUEST_COUNT.incrementAndGet();
                String bookingIdRaw = path.substring("/api/core/bookings/".length());
                BookingStub booking = parseBookingId(bookingIdRaw) == null ? null : BOOKINGS.get(parseBookingId(bookingIdRaw));
                if (booking != null) {
                    return ok(200, booking.toApiSuccessJson());
                }
                return new MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("""
                        {"success":true,"data":{"id":"%s"},"error":null}
                        """.formatted(bookingIdRaw));
            }
            if ("POST".equals(request.getMethod()) && "/api/core/venues".equals(path)) {
                CORE_REQUEST_COUNT.incrementAndGet();
                return new MockResponse()
                    .setResponseCode(201)
                    .addHeader("Content-Type", "application/json")
                    .setBody("{\"id\":\"%s\"}".formatted(UUID.randomUUID()));
            }
            if ("POST".equals(request.getMethod()) && "/api/core/bookings/draft".equals(path)) {
                try {
                    JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
                    BookingStub booking = BookingStub.fromDraft(body);
                    BOOKINGS.put(booking.id(), booking);
                    return ok(201, booking.toApiSuccessJson());
                } catch (Exception e) {
                    return new MockResponse().setResponseCode(400);
                }
            }
            if ("POST".equals(request.getMethod()) && path != null && path.matches("^/api/core/bookings/[0-9a-fA-F\\-]+/deposit$")) {
                UUID bookingId = parseBookingId(path.split("/")[4]);
                BookingStub booking = BOOKINGS.get(bookingId);
                if (booking == null) {
                    return new MockResponse().setResponseCode(404);
                }
                try {
                    JsonNode body = MAPPER.readTree(request.getBody().readUtf8());
                    BigDecimal amount = new BigDecimal(body.path("amount").asText("0"));
                    booking.deposit(amount);
                    return ok(200, booking.toApiSuccessJson());
                } catch (Exception e) {
                    return new MockResponse().setResponseCode(400);
                }
            }
            if ("POST".equals(request.getMethod()) && path != null && path.matches("^/api/core/bookings/[0-9a-fA-F\\-]+/confirm$")) {
                UUID bookingId = parseBookingId(path.split("/")[4]);
                BookingStub booking = BOOKINGS.get(bookingId);
                if (booking == null) {
                    return new MockResponse().setResponseCode(404);
                }
                if (!booking.canConfirm()) {
                    return new MockResponse()
                        .setResponseCode(409)
                        .addHeader("Content-Type", "application/json")
                        .setBody("{\"success\":false,\"data\":null,\"error\":\"Deposit required\"}");
                }
                booking.confirm();
                return ok(200, booking.toApiSuccessJson());
            }
            if ("GET".equals(request.getMethod()) && "/api/core/availability".equals(path)) {
                try {
                    Map<String, String> query = parseQueryParams(fullPath);
                    UUID courtId = UUID.fromString(query.get("courtId"));
                    OffsetDateTime start = OffsetDateTime.parse(query.get("start").replace(" ", "+"));
                    OffsetDateTime end = OffsetDateTime.parse(query.get("end").replace(" ", "+"));
                    boolean available = BOOKINGS.values().stream().noneMatch(booking ->
                        booking.isConfirmed()
                            && booking.courtId().equals(courtId)
                            && booking.overlaps(start, end)
                    );
                    return ok(200, """
                        {"success":true,"data":{"available":%s},"error":null}
                        """.formatted(available));
                } catch (Exception e) {
                    return new MockResponse().setResponseCode(400);
                }
            }
            return new MockResponse().setResponseCode(404);
        }
    }

    private static MockResponse ok(int status, String body) {
        return new MockResponse()
            .setResponseCode(status)
            .addHeader("Content-Type", "application/json")
            .setBody(body);
    }

    private static UUID parseBookingId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private static String pathWithoutQuery(String rawPath) {
        if (rawPath == null) {
            return null;
        }
        int queryIndex = rawPath.indexOf('?');
        return queryIndex >= 0 ? rawPath.substring(0, queryIndex) : rawPath;
    }

    private static Map<String, String> parseQueryParams(String rawPath) {
        Map<String, String> params = new java.util.HashMap<>();
        if (rawPath == null) {
            return params;
        }
        int queryIndex = rawPath.indexOf('?');
        if (queryIndex < 0 || queryIndex == rawPath.length() - 1) {
            return params;
        }
        String query = rawPath.substring(queryIndex + 1);
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            String[] pieces = pair.split("=", 2);
            String key = URLDecoder.decode(pieces[0], StandardCharsets.UTF_8);
            String value = pieces.length > 1 ? URLDecoder.decode(pieces[1], StandardCharsets.UTF_8) : "";
            params.put(key, value);
        }
        return params;
    }

    private static String toJsonArray(List<String> values) {
        return values.stream()
            .map(value -> "\"" + value + "\"")
            .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String issueToken(UUID userId, String email, List<String> roles) throws JOSEException {
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
            .issuer(JWT_ISSUER)
            .subject(userId.toString())
            .issueTime(java.util.Date.from(Instant.now()))
            .expirationTime(java.util.Date.from(Instant.now().plusSeconds(3600)))
            .claim("email", email)
            .claim("roles", roles)
            .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
            .keyID(JWT_KID)
            .build();
        SignedJWT signedJWT = new SignedJWT(header, claimsSet);
        signedJWT.sign(new RSASSASigner(gatewayTestRsaKey.toPrivateKey()));
        return signedJWT.serialize();
    }

    private static final class BookingStub {
        private final UUID id;
        private final UUID courtId;
        private final UUID customerId;
        private final OffsetDateTime startTime;
        private final OffsetDateTime endTime;
        private final BigDecimal priceTotal;
        private final BigDecimal depositRequired;
        private BigDecimal depositPaid;
        private String status;
        private String paymentStatus;

        private BookingStub(UUID id,
                            UUID courtId,
                            UUID customerId,
                            OffsetDateTime startTime,
                            OffsetDateTime endTime,
                            BigDecimal priceTotal,
                            BigDecimal depositRequired,
                            BigDecimal depositPaid,
                            String status,
                            String paymentStatus) {
            this.id = id;
            this.courtId = courtId;
            this.customerId = customerId;
            this.startTime = startTime;
            this.endTime = endTime;
            this.priceTotal = priceTotal;
            this.depositRequired = depositRequired;
            this.depositPaid = depositPaid;
            this.status = status;
            this.paymentStatus = paymentStatus;
        }

        static BookingStub fromDraft(JsonNode body) {
            UUID id = UUID.randomUUID();
            UUID courtId = UUID.fromString(body.path("courtId").asText());
            UUID customerId = UUID.fromString(body.path("customerId").asText());
            OffsetDateTime startTime = OffsetDateTime.parse(body.path("startTime").asText());
            OffsetDateTime endTime = OffsetDateTime.parse(body.path("endTime").asText());
            BigDecimal priceTotal = new BigDecimal(body.path("priceTotal").asText("0"));
            BigDecimal depositRequired = priceTotal.divide(new BigDecimal("2"));
            return new BookingStub(
                id,
                courtId,
                customerId,
                startTime,
                endTime,
                priceTotal,
                depositRequired,
                BigDecimal.ZERO,
                "DRAFT",
                "UNPAID"
            );
        }

        UUID id() {
            return id;
        }

        UUID courtId() {
            return courtId;
        }

        boolean isConfirmed() {
            return "CONFIRMED".equals(status);
        }

        boolean overlaps(OffsetDateTime start, OffsetDateTime end) {
            return startTime.isBefore(end) && endTime.isAfter(start);
        }

        void deposit(BigDecimal amount) {
            this.depositPaid = amount;
            if (amount.compareTo(depositRequired) >= 0) {
                this.paymentStatus = "DEPOSITED";
            }
        }

        boolean canConfirm() {
            return "DEPOSITED".equals(paymentStatus);
        }

        void confirm() {
            this.status = "CONFIRMED";
        }

        String toApiSuccessJson() {
            return """
                {"success":true,"data":{
                  "id":"%s",
                  "courtId":"%s",
                  "customerId":"%s",
                  "status":"%s",
                  "paymentStatus":"%s",
                  "startTime":"%s",
                  "endTime":"%s",
                  "priceTotal":%s,
                  "depositRequired":%s,
                  "depositPaid":%s
                },"error":null}
                """.formatted(
                id,
                courtId,
                customerId,
                status,
                paymentStatus,
                startTime,
                endTime,
                priceTotal,
                depositRequired,
                depositPaid
            );
        }
    }
}
