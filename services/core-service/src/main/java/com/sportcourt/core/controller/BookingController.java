package com.sportcourt.core.controller;

import com.sportcourt.core.api.ApiResponse;
import com.sportcourt.core.api.PageResponse;
import com.sportcourt.core.domain.enums.BookingStatus;
import com.sportcourt.core.dto.BatchBookingActionResponse;
import com.sportcourt.core.dto.BatchBookingDraftRequest;
import com.sportcourt.core.dto.BatchBookingDraftResponse;
import com.sportcourt.core.dto.BatchConfirmRequest;
import com.sportcourt.core.dto.BatchDepositRequest;
import com.sportcourt.core.dto.BookingDraftRequest;
import com.sportcourt.core.dto.BookingResponse;
import com.sportcourt.core.dto.DepositPaymentRequest;
import com.sportcourt.core.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/core/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/draft")
    public ResponseEntity<ApiResponse<BookingResponse>> draft(@Valid @RequestBody BookingDraftRequest req,
                                                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                              @AuthenticationPrincipal Jwt jwt) {
        UUID customerId = resolveCustomerId(req.customerId(), jwt);
        BookingDraftRequest effectiveReq = new BookingDraftRequest(
            req.courtId(),
            customerId,
            req.startTime(),
            req.endTime(),
            req.priceTotal()
        );
        BookingResponse response = bookingService.createDraft(effectiveReq, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    public ApiResponse<BookingResponse> getById(@PathVariable UUID id,
                                                @AuthenticationPrincipal Jwt jwt) {
        BookingResponse response = bookingService.getById(id);
        verifyCustomerOwnership(response, jwt);
        return ApiResponse.success(response);
    }

    @GetMapping
    public ApiResponse<PageResponse<BookingResponse>> list(
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) UUID courtId,
        @RequestParam(required = false) BookingStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @AuthenticationPrincipal Jwt jwt,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID effectiveCustomerId = customerId;
        if (isCustomer(jwt)) {
            effectiveCustomerId = extractActorId(jwt);
        }
        return ApiResponse.success(bookingService.list(effectiveCustomerId, courtId, status, from, to, pageable));
    }

    @PostMapping("/draft/batch")
    public ResponseEntity<ApiResponse<BatchBookingDraftResponse>> draftBatch(
        @Valid @RequestBody BatchBookingDraftRequest req,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID customerId = resolveCustomerId(req.customerId(), jwt);
        BatchBookingDraftRequest effectiveReq = new BatchBookingDraftRequest(customerId, req.items());
        BatchBookingDraftResponse response = bookingService.createBatchDraft(effectiveReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @PostMapping("/{id}/deposit")
    public ApiResponse<BookingResponse> deposit(@PathVariable UUID id,
                                                @Valid @RequestBody DepositPaymentRequest req,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                @AuthenticationPrincipal Jwt jwt) {
        verifyCustomerOwnership(bookingService.getById(id), jwt);
        return ApiResponse.success(bookingService.payDeposit(id, req, idempotencyKey));
    }

    @PostMapping("/deposit/batch")
    public ApiResponse<BatchBookingActionResponse> depositBatch(@Valid @RequestBody BatchDepositRequest req,
                                                                @AuthenticationPrincipal Jwt jwt) {
        if (isCustomer(jwt)) {
            req.items().forEach(item -> verifyCustomerOwnership(bookingService.getById(item.bookingId()), jwt));
        }
        return ApiResponse.success(bookingService.payDepositBatch(req));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<BookingResponse> confirm(@PathVariable UUID id,
                                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                @AuthenticationPrincipal Jwt jwt) {
        verifyCustomerOwnership(bookingService.getById(id), jwt);
        return ApiResponse.success(bookingService.confirm(id, idempotencyKey));
    }

    @PostMapping("/confirm/batch")
    public ApiResponse<BatchBookingActionResponse> confirmBatch(@Valid @RequestBody BatchConfirmRequest req,
                                                                @AuthenticationPrincipal Jwt jwt) {
        if (isCustomer(jwt)) {
            req.bookingIds().forEach(bookingId -> verifyCustomerOwnership(bookingService.getById(bookingId), jwt));
        }
        return ApiResponse.success(bookingService.confirmBatch(req));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<BookingResponse> cancel(@PathVariable UUID id,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                               @AuthenticationPrincipal Jwt jwt) {
        verifyCustomerOwnership(bookingService.getById(id), jwt);
        return ApiResponse.success(bookingService.cancel(id, idempotencyKey));
    }

    private UUID resolveCustomerId(UUID requestedCustomerId, Jwt jwt) {
        UUID actorId = extractActorId(jwt);
        if (isCustomer(jwt)) {
            return actorId;
        }
        return requestedCustomerId != null ? requestedCustomerId : actorId;
    }

    private void verifyCustomerOwnership(BookingResponse booking, Jwt jwt) {
        if (!isCustomer(jwt)) {
            return;
        }
        UUID actorId = extractActorId(jwt);
        if (booking.customerId() == null || !booking.customerId().equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot access other customer's booking");
        }
    }

    private boolean isCustomer(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles == null) {
            return false;
        }
        return roles.stream().anyMatch(this::isCustomerRole);
    }

    private boolean isCustomerRole(String role) {
        return "CUSTOMER".equalsIgnoreCase(role) || "ROLE_CUSTOMER".equalsIgnoreCase(role);
    }

    private UUID extractActorId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token subject");
        }
    }
}
