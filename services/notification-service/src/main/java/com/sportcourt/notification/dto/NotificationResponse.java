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
    String title,
    String deepLink,
    String message,
    Map<String, String> metadata,
    UUID bookingId,
    UUID paymentId,
    UUID customerId,
    String sourceEventId,
    String sourceEventType,
    String traceId,
    int retryCount,
    String lastError,
    OffsetDateTime createdAt,
    OffsetDateTime sentAt,
    OffsetDateTime readAt,
    boolean unread,
    OffsetDateTime updatedAt
) {
}
