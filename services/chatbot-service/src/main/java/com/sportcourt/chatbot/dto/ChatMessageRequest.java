package com.sportcourt.chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ChatMessageRequest(
    @NotBlank @Size(max = 64) String sessionId,
    UUID userId,
    @NotBlank @Size(max = 2000) String message
) {
}
