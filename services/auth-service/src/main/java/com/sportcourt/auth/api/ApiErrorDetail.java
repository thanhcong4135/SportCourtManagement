package com.sportcourt.auth.api;

public record ApiErrorDetail(
    String field,
    String message
) {
}
