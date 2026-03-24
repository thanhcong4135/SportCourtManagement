package com.sportcourt.notification.service;

import com.sportcourt.notification.dto.NotificationResponse;
import com.sportcourt.notification.dto.NotificationSendRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {

    private final Map<UUID, NotificationResponse> notifications = new ConcurrentHashMap<>();

    public NotificationResponse send(NotificationSendRequest request) {
        UUID notificationId = UUID.randomUUID();
        NotificationResponse response = new NotificationResponse(
            notificationId,
            "QUEUED",
            request.recipient(),
            request.channel(),
            request.templateCode(),
            request.message(),
            request.metadata(),
            OffsetDateTime.now(ZoneOffset.UTC)
        );
        notifications.put(notificationId, response);
        return response;
    }

    public NotificationResponse getById(UUID notificationId) {
        NotificationResponse response = notifications.get(notificationId);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        }
        return response;
    }
}
