package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationChannel;

import java.util.Map;
import java.util.UUID;

public record NotificationEventCommand(
    String sourceTopic,
    String sourceEventId,
    String sourceEventType,
    UUID bookingId,
    UUID paymentId,
    UUID customerId,
    String traceId,
    NotificationChannel channel,
    String recipient,
    String templateCode,
    String title,
    String deepLink,
    String message,
    Map<String, String> metadata
) {
}
