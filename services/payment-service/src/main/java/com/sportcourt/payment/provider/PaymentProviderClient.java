package com.sportcourt.payment.provider;

import com.sportcourt.payment.domain.PaymentTransaction;
import com.sportcourt.payment.domain.enums.PaymentProvider;

public interface PaymentProviderClient {

    PaymentProvider provider();

    ProviderPaymentSession createDepositSession(PaymentTransaction paymentTransaction);
}
