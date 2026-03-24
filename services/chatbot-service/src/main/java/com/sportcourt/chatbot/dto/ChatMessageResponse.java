package com.sportcourt.chatbot.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ChatMessageResponse(
    String sessionId,
    UUID userId,
    String intent,
    double confidence,
    String response,
    OffsetDateTime createdAt
) {
}
