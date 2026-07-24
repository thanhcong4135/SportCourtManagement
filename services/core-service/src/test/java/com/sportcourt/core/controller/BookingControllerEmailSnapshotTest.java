package com.sportcourt.core.controller;

import com.sportcourt.core.dto.BookingDraftRequest;
import com.sportcourt.core.service.BookingService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class BookingControllerEmailSnapshotTest {

    @Test
    void customerRequestUsesJwtIdentityAndEmail() {
        BookingService bookingService = mock(BookingService.class);
        BookingController controller = new BookingController(bookingService);
        UUID actorId = UUID.randomUUID();
        BookingDraftRequest request = request(UUID.randomUUID(), "other@example.com");

        controller.draft(request, null, jwt(actorId, " Customer@Example.COM ", "CUSTOMER"));

        ArgumentCaptor<BookingDraftRequest> captor = ArgumentCaptor.forClass(BookingDraftRequest.class);
        verify(bookingService).createDraft(captor.capture(), org.mockito.ArgumentMatchers.isNull());
        assertThat(captor.getValue().customerId()).isEqualTo(actorId);
        assertThat(captor.getValue().customerEmail()).isEqualTo("customer@example.com");
    }

    @Test
    void ownerBookingForAnotherCustomerMayProvideEmailSnapshot() {
        BookingService bookingService = mock(BookingService.class);
        BookingController controller = new BookingController(bookingService);
        UUID actorId = UUID.randomUUID();
        UUID requestedCustomerId = UUID.randomUUID();
        BookingDraftRequest request = request(requestedCustomerId, " Other@Example.COM ");

        controller.draft(request, null, jwt(actorId, "owner@example.com", "OWNER"));

        ArgumentCaptor<BookingDraftRequest> captor = ArgumentCaptor.forClass(BookingDraftRequest.class);
        verify(bookingService).createDraft(captor.capture(), org.mockito.ArgumentMatchers.isNull());
        assertThat(captor.getValue().customerId()).isEqualTo(requestedCustomerId);
        assertThat(captor.getValue().customerEmail()).isEqualTo("other@example.com");
    }

    private BookingDraftRequest request(UUID customerId, String email) {
        return new BookingDraftRequest(
            UUID.randomUUID(),
            customerId,
            OffsetDateTime.of(2026, 8, 1, 8, 0, 0, 0, ZoneOffset.UTC),
            OffsetDateTime.of(2026, 8, 1, 9, 0, 0, 0, ZoneOffset.UTC),
            new BigDecimal("100000"),
            email
        );
    }

    private Jwt jwt(UUID subject, String email, String role) {
        return Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject(subject.toString())
            .claim("email", email)
            .claim("roles", List.of(role))
            .build();
    }
}
