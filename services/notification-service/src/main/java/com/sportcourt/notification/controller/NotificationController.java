package com.sportcourt.notification.controller;

import com.sportcourt.notification.domain.enums.NotificationStatus;
import com.sportcourt.notification.dto.NotificationResponse;
import com.sportcourt.notification.dto.NotificationSendRequest;
import com.sportcourt.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping("/send")
    public ResponseEntity<NotificationResponse> send(@Valid @RequestBody NotificationSendRequest request) {
        NotificationResponse response = notificationService.send(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{notificationId}")
    public NotificationResponse getById(@PathVariable UUID notificationId) {
        return notificationService.getById(notificationId);
    }

    @GetMapping
    public Page<NotificationResponse> list(
        @RequestParam(required = false) UUID bookingId,
        @RequestParam(required = false) UUID customerId,
        @RequestParam(required = false) NotificationStatus status,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return notificationService.list(bookingId, customerId, status, pageable);
    }

    @PostMapping("/{notificationId}/retry")
    public NotificationResponse retry(@PathVariable UUID notificationId) {
        return notificationService.retry(notificationId);
    }
}
