package com.sportcourt.core.api;

public record ApiErrorDetail(
    String field,
    String message
) {
}
