package com.sportcourt.payment.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.payment.domain.PaymentTransaction;
import com.sportcourt.payment.domain.enums.OutboxEventStatus;
import com.sportcourt.payment.domain.enums.PaymentTransactionStatus;
import com.sportcourt.payment.outbox.OutboxEvent;
import com.sportcourt.payment.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class PaymentOutboxService {

    private static final String AGGREGATE_TYPE = "PAYMENT";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String paymentTopic;

    public PaymentOutboxService(OutboxEventRepository outboxEventRepository,
                                ObjectMapper objectMapper,
                                @Value("${kafka.topics.payment-events}") String paymentTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.paymentTopic = paymentTopic;
    }

    public void enqueueDepositResult(PaymentTransaction payment) {
        PaymentEventType eventType = toEventType(payment.getStatus());
        if (eventType == null) {
            return;
        }

        UUID eventId = UUID.randomUUID();
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(eventId);
        outboxEvent.setAggregateType(AGGREGATE_TYPE);
        outboxEvent.setAggregateId(payment.getId());
        outboxEvent.setEventType(eventType.name());
        outboxEvent.setTopic(paymentTopic);
        outboxEvent.setEventKey(payment.getBookingId().toString());
        outboxEvent.setPayload(toPayload(eventId, eventType, payment));
        outboxEvent.setStatus(OutboxEventStatus.PENDING);
        outboxEvent.setRetryCount(0);
        outboxEvent.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC));
        outboxEvent.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        outboxEventRepository.save(outboxEvent);
    }

    private PaymentEventType toEventType(PaymentTransactionStatus status) {
        if (status == PaymentTransactionStatus.SUCCESS) {
            return PaymentEventType.DEPOSIT_SUCCEEDED;
        }
        if (status == PaymentTransactionStatus.FAILED) {
            return PaymentEventType.DEPOSIT_FAILED;
        }
        return null;
    }

    private String toPayload(UUID eventId, PaymentEventType eventType, PaymentTransaction payment) {
        PaymentEvent event = new PaymentEvent();
        event.setEventId(eventId);
        event.setType(eventType);
        event.setPaymentId(payment.getId());
        event.setBookingId(payment.getBookingId());
        event.setCustomerId(payment.getCustomerId());
        event.setAmount(payment.getAmount());
        event.setProviderReference(payment.getProviderReference());
        event.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment event " + payment.getId(), e);
        }
    }
}
