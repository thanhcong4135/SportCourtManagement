package com.sportcourt.payment.provider;

public record ProviderPaymentSession(
    String providerReference,
    String checkoutUrl
) {
}
