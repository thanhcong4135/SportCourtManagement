package com.sportcourt.notification.dto;

import com.sportcourt.notification.domain.NotificationChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record NotificationSendRequest(
    @NotBlank @Size(max = 128) String recipient,
    @NotNull NotificationChannel channel,
    @Size(max = 128) String templateCode,
    @NotBlank @Size(max = 2000) String message,
    Map<String, String> metadata
) {
}
