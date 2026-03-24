package com.sportcourt.notification.controller;

import com.sportcourt.notification.dto.NotificationResponse;
import com.sportcourt.notification.dto.NotificationSendRequest;
import com.sportcourt.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
