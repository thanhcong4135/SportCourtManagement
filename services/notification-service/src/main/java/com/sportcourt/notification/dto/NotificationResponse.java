package com.sportcourt.notification.dto;

import com.sportcourt.notification.domain.NotificationChannel;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String status,
    String recipient,
    NotificationChannel channel,
    String templateCode,
    String message,
    Map<String, String> metadata,
    OffsetDateTime createdAt
) {
}
