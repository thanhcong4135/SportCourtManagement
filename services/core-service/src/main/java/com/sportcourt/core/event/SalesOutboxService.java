package com.sportcourt.core.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.core.domain.OutboxEvent;
import com.sportcourt.core.domain.SalesOrder;
import com.sportcourt.core.domain.enums.OutboxEventStatus;
import com.sportcourt.core.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class SalesOutboxService {

    private static final String AGGREGATE_TYPE = "SALES_ORDER";
    private static final String SCHEMA_VERSION = "1.0";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String salesTopic;

    public SalesOutboxService(OutboxEventRepository outboxEventRepository,
                              ObjectMapper objectMapper,
                              @Value("${kafka.topics.sales-events}") String salesTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.salesTopic = salesTopic;
    }

    public void enqueueCreated(SalesOrder salesOrder) {
        UUID eventId = UUID.randomUUID();
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(eventId);
        outboxEvent.setAggregateType(AGGREGATE_TYPE);
        outboxEvent.setAggregateId(salesOrder.getId());
        outboxEvent.setEventType(SalesEventType.SALES_ORDER_CREATED.name());
        outboxEvent.setTopic(salesTopic);
        outboxEvent.setEventKey(salesOrder.getId().toString());
        outboxEvent.setPayload(toPayload(eventId, salesOrder));
        outboxEvent.setStatus(OutboxEventStatus.PENDING);
        outboxEvent.setRetryCount(0);
        outboxEvent.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC));
        outboxEvent.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        outboxEventRepository.save(outboxEvent);
    }

    private String toPayload(UUID eventId, SalesOrder salesOrder) {
        SalesEvent event = new SalesEvent();
        event.setSchemaVersion(SCHEMA_VERSION);
        event.setEventId(eventId);
        event.setType(SalesEventType.SALES_ORDER_CREATED);
        event.setOrderId(salesOrder.getId());
        event.setBookingId(salesOrder.getBooking() != null ? salesOrder.getBooking().getId() : null);
        event.setVenueId(salesOrder.getVenue() != null ? salesOrder.getVenue().getId() : null);
        event.setCustomerId(salesOrder.getCustomerId());
        event.setStatus(salesOrder.getStatus());
        event.setTotalAmount(salesOrder.getTotalAmount());
        event.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize sales event " + salesOrder.getId(), ex);
        }
    }
}
