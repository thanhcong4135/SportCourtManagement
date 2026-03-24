package com.sportcourt.payment.provider;

import com.sportcourt.payment.domain.enums.PaymentProvider;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class PaymentProviderClientResolver {

    private final Map<PaymentProvider, PaymentProviderClient> clientsByProvider;

    public PaymentProviderClientResolver(List<PaymentProviderClient> clients) {
        Map<PaymentProvider, PaymentProviderClient> temp = new EnumMap<>(PaymentProvider.class);
        for (PaymentProviderClient client : clients) {
            temp.put(client.provider(), client);
        }
        this.clientsByProvider = Map.copyOf(temp);
    }

    public PaymentProviderClient resolve(PaymentProvider provider) {
        PaymentProviderClient client = clientsByProvider.get(provider);
        if (client == null) {
            throw new IllegalStateException("No payment provider client registered for: " + provider);
        }
        return client;
    }
}
