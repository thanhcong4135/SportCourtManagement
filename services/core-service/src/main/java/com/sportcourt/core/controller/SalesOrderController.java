package com.sportcourt.core.controller;

import com.sportcourt.core.api.ApiResponse;
import com.sportcourt.core.api.PageResponse;
import com.sportcourt.core.dto.SalesOrderCreateRequest;
import com.sportcourt.core.dto.SalesOrderResponse;
import com.sportcourt.core.service.SalesOrderService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/core/orders")
public class SalesOrderController {

    private final SalesOrderService salesOrderService;

    public SalesOrderController(SalesOrderService salesOrderService) {
        this.salesOrderService = salesOrderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SalesOrderResponse>> create(@Valid @RequestBody SalesOrderCreateRequest req,
                                                                  @AuthenticationPrincipal Jwt jwt) {
        UUID effectiveCustomerId = resolveCustomerId(req.customerId(), jwt);
        SalesOrderCreateRequest effectiveReq = new SalesOrderCreateRequest(
            req.bookingId(),
            req.venueId(),
            effectiveCustomerId,
            req.items()
        );
        SalesOrderResponse response = salesOrderService.create(effectiveReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<PageResponse<SalesOrderResponse>> list(
        @RequestParam(required = false) UUID bookingId,
        @RequestParam(required = false) UUID venueId,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
        @AuthenticationPrincipal Jwt jwt,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        UUID effectiveCustomerId = customerId;
        if (isCustomer(jwt)) {
            effectiveCustomerId = extractActorId(jwt);
        }
        return ApiResponse.success(salesOrderService.list(bookingId, venueId, effectiveCustomerId, from, to, pageable));
    }

    private UUID resolveCustomerId(UUID requestedCustomerId, Jwt jwt) {
        UUID actorId = extractActorId(jwt);
        if (isCustomer(jwt)) {
            return actorId;
        }
        return requestedCustomerId != null ? requestedCustomerId : actorId;
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
