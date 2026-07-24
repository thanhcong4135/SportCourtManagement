package com.sportcourt.notification.controller;

import com.sportcourt.notification.dto.NotificationCountResponse;
import com.sportcourt.notification.dto.NotificationResponse;
import com.sportcourt.notification.dto.NotificationUpdatedResponse;
import com.sportcourt.notification.service.CustomerNotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/me")
public class CustomerNotificationController {

    private final CustomerNotificationService customerNotificationService;

    public CustomerNotificationController(CustomerNotificationService customerNotificationService) {
        this.customerNotificationService = customerNotificationService;
    }

    @GetMapping
    public Page<NotificationResponse> listMine(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return customerNotificationService.listMine(customerId(jwt), unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    public NotificationCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
        return new NotificationCountResponse(customerNotificationService.countUnread(customerId(jwt)));
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponse markRead(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID notificationId) {
        return customerNotificationService.markRead(customerId(jwt), notificationId);
    }

    @PatchMapping("/read-all")
    public NotificationUpdatedResponse markAllRead(@AuthenticationPrincipal Jwt jwt) {
        return new NotificationUpdatedResponse(customerNotificationService.markAllRead(customerId(jwt)));
    }

    private UUID customerId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated subject");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authenticated subject");
        }
    }
}
