package com.sportcourt.core.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.core.event.PaymentEvent;
import com.sportcourt.core.service.PaymentEventProcessor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventConsumer {

    private static final String SUPPORTED_SCHEMA_VERSION = "1.0";

    private final ObjectMapper objectMapper;
    private final PaymentEventProcessor paymentEventProcessor;

    public PaymentEventConsumer(ObjectMapper objectMapper, PaymentEventProcessor paymentEventProcessor) {
        this.objectMapper = objectMapper;
        this.paymentEventProcessor = paymentEventProcessor;
    }

    @KafkaListener(
        topics = "${kafka.topics.payment-events}",
        groupId = "${kafka.consumer.payment.group-id:core-service-payment}",
        autoStartup = "${kafka.consumer.payment.enabled:true}"
    )
    public void consumePaymentEvent(@Payload String payload,
                                    @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                    @Header(name = "event-id", required = false) String headerEventId) {
        try {
            PaymentEvent paymentEvent = objectMapper.readValue(payload, PaymentEvent.class);
            validateSchemaVersion(paymentEvent.getSchemaVersion());
            String eventId = resolveEventId(paymentEvent, headerEventId);
            paymentEventProcessor.process(paymentEvent, topic, eventId);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to process payment event", ex);
        }
    }

    private String resolveEventId(PaymentEvent paymentEvent, String headerEventId) {
        if (paymentEvent.getEventId() != null) {
            return paymentEvent.getEventId().toString();
        }
        if (headerEventId != null && !headerEventId.isBlank()) {
            return headerEventId;
        }
        if (paymentEvent.getPaymentId() != null && paymentEvent.getType() != null) {
            return paymentEvent.getPaymentId() + "-" + paymentEvent.getType().name();
        }
        return null;
    }

    private void validateSchemaVersion(String schemaVersion) {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            return;
        }
        if (!SUPPORTED_SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported payment event schemaVersion: " + schemaVersion);
        }
    }
}
