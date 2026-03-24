package com.sportcourt.payment.domain.enums;

import java.util.Locale;

public enum PaymentProvider {
    MOCK;

    public static PaymentProvider fromConfig(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return MOCK;
        }
        try {
            return PaymentProvider.valueOf(rawValue.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unsupported payment provider: " + rawValue, exception);
        }
    }
}
