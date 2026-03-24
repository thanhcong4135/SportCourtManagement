package com.sportcourt.notification.api;

public record ApiErrorDetail(
    String field,
    String message
) {
}
