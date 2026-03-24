package com.sportcourt.gateway.api;

public record ApiErrorDetail(
    String field,
    String message
) {
}
