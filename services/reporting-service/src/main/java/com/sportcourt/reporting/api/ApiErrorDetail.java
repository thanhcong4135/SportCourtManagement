package com.sportcourt.reporting.api;

public record ApiErrorDetail(
    String field,
    String message
) {
}
