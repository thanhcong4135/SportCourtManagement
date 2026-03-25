package com.sportcourt.core.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.core.domain.Booking;
import com.sportcourt.core.domain.OutboxEvent;
import com.sportcourt.core.domain.enums.OutboxEventStatus;
import com.sportcourt.core.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class BookingOutboxService {

    private static final String AGGREGATE_TYPE = "BOOKING";
    private static final String SCHEMA_VERSION = "1.0";

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String bookingTopic;

    public BookingOutboxService(OutboxEventRepository outboxEventRepository,
                                ObjectMapper objectMapper,
                                @Value("${kafka.topics.booking-events}") String bookingTopic) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.bookingTopic = bookingTopic;
    }

    public void enqueue(BookingEventType type, Booking booking) {
        UUID eventId = UUID.randomUUID();
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(eventId);
        outboxEvent.setAggregateType(AGGREGATE_TYPE);
        outboxEvent.setAggregateId(booking.getId());
        outboxEvent.setEventType(type.name());
        outboxEvent.setTopic(bookingTopic);
        outboxEvent.setEventKey(booking.getId().toString());
        outboxEvent.setPayload(toPayload(eventId, type, booking));
        outboxEvent.setStatus(OutboxEventStatus.PENDING);
        outboxEvent.setRetryCount(0);
        outboxEvent.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC));
        outboxEvent.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        outboxEventRepository.save(outboxEvent);
    }

    private String toPayload(UUID eventId, BookingEventType type, Booking booking) {
        BookingEvent event = new BookingEvent();
        event.setSchemaVersion(SCHEMA_VERSION);
        event.setEventId(eventId);
        event.setType(type);
        event.setBookingId(booking.getId());
        event.setCourtId(booking.getCourt() != null ? booking.getCourt().getId() : null);
        event.setCustomerId(booking.getCustomerId());
        event.setStatus(booking.getStatus());
        event.setPaymentStatus(booking.getPaymentStatus());
        event.setStartTime(booking.getStartTime());
        event.setEndTime(booking.getEndTime());
        event.setPriceTotal(booking.getPriceTotal());
        event.setOccurredAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize booking event " + booking.getId(), e);
        }
    }
}
