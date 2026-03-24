package com.sportcourt.payment.api;

public record ApiErrorDetail(
    String field,
    String message
) {
}
