package com.sportcourt.payment.provider;

import com.sportcourt.payment.domain.PaymentTransaction;
import com.sportcourt.payment.domain.enums.PaymentProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class MockPaymentProviderClient implements PaymentProviderClient {

    private final String checkoutBaseUrl;

    public MockPaymentProviderClient(
        @Value("${payment.provider.mock.checkout-base-url:http://localhost:8083/mock-pay}") String checkoutBaseUrl
    ) {
        this.checkoutBaseUrl = checkoutBaseUrl;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.MOCK;
    }

    @Override
    public ProviderPaymentSession createDepositSession(PaymentTransaction paymentTransaction) {
        String providerReference = "MOCK-" + paymentTransaction.getId();
        String checkoutUrl = UriComponentsBuilder.fromUriString(checkoutBaseUrl)
            .queryParam("paymentId", paymentTransaction.getId())
            .queryParam("bookingId", paymentTransaction.getBookingId())
            .queryParam("amount", paymentTransaction.getAmount())
            .queryParam("currency", paymentTransaction.getCurrency())
            .build()
            .toUriString();
        return new ProviderPaymentSession(providerReference, checkoutUrl);
    }
}
