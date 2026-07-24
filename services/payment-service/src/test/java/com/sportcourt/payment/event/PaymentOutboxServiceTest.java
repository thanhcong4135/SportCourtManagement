package com.sportcourt.payment.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.payment.domain.PaymentTransaction;
import com.sportcourt.payment.domain.enums.PaymentTransactionStatus;
import com.sportcourt.payment.outbox.OutboxEvent;
import com.sportcourt.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PaymentOutboxServiceTest {

    @Test
    void depositResultRetainsCustomerEmailInSchemaOnePointOne() throws Exception {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        PaymentOutboxService service = new PaymentOutboxService(repository, objectMapper, "payment.events");
        PaymentTransaction payment = new PaymentTransaction();
        payment.setId(UUID.randomUUID());
        payment.setBookingId(UUID.randomUUID());
        payment.setCustomerId(UUID.randomUUID());
        payment.setCustomerEmail("customer@example.com");
        payment.setAmount(new BigDecimal("100000"));
        payment.setStatus(PaymentTransactionStatus.SUCCESS);

        service.enqueueDepositResult(payment);

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        var payload = objectMapper.readTree(captor.getValue().getPayload());
        assertThat(payload.path("schemaVersion").asText()).isEqualTo("1.1");
        assertThat(payload.path("customerEmail").asText()).isEqualTo("customer@example.com");
    }
}
