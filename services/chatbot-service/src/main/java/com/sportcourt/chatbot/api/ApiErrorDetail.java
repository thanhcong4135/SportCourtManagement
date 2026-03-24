package com.sportcourt.chatbot.api;

public record ApiErrorDetail(
    String field,
    String message
) {
}
