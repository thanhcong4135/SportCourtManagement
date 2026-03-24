package com.sportcourt.core.scheduler;

import com.sportcourt.core.domain.OutboxEvent;
import com.sportcourt.core.domain.enums.OutboxEventStatus;
import com.sportcourt.core.event.BookingEventPublisher;
import com.sportcourt.core.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OutboxPublisherSchedulerTest {

    @Test
    void publishPendingEvents_whenSuccess_shouldMarkSent() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        BookingEventPublisher publisher = mock(BookingEventPublisher.class);
        OutboxPublisherScheduler scheduler = new OutboxPublisherScheduler(
            repository,
            publisher,
            new SimpleMeterRegistry(),
            100,
            3,
            5,
            60
        );
        OutboxEvent event = pendingEvent();

        when(repository.findBatchForPublish(
            ArgumentMatchers.eq(OutboxEventStatus.PENDING),
            ArgumentMatchers.any(OffsetDateTime.class),
            ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(event));

        scheduler.publishPendingEvents();

        verify(publisher).publishRaw(event.getTopic(), event.getEventKey(), event.getPayload(), event.getId().toString());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(event.getSentAt()).isNotNull();
        assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    void publishPendingEvents_whenPublishFails_shouldScheduleRetry() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        BookingEventPublisher publisher = mock(BookingEventPublisher.class);
        OutboxPublisherScheduler scheduler = new OutboxPublisherScheduler(
            repository,
            publisher,
            new SimpleMeterRegistry(),
            100,
            3,
            5,
            60
        );
        OutboxEvent event = pendingEvent();

        when(repository.findBatchForPublish(
            ArgumentMatchers.eq(OutboxEventStatus.PENDING),
            ArgumentMatchers.any(OffsetDateTime.class),
            ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(event));
        doThrow(new IllegalStateException("kafka down")).when(publisher)
            .publishRaw(anyString(), anyString(), anyString(), anyString());

        scheduler.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getRetryCount()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        assertThat(event.getLastError()).contains("kafka down");
    }

    @Test
    void publishPendingEvents_whenRetryExhausted_shouldMarkFailed() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        BookingEventPublisher publisher = mock(BookingEventPublisher.class);
        OutboxPublisherScheduler scheduler = new OutboxPublisherScheduler(
            repository,
            publisher,
            new SimpleMeterRegistry(),
            100,
            3,
            5,
            60
        );
        OutboxEvent event = pendingEvent();
        event.setRetryCount(2);

        when(repository.findBatchForPublish(
            ArgumentMatchers.eq(OutboxEventStatus.PENDING),
            ArgumentMatchers.any(OffsetDateTime.class),
            ArgumentMatchers.any(Pageable.class)
        )).thenReturn(List.of(event));
        doThrow(new IllegalStateException("kafka down")).when(publisher)
            .publishRaw(anyString(), anyString(), anyString(), anyString());

        scheduler.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
        assertThat(event.getRetryCount()).isEqualTo(3);
        assertThat(event.getNextAttemptAt()).isNull();
    }

    private OutboxEvent pendingEvent() {
        OutboxEvent event = new OutboxEvent();
        event.setId(UUID.randomUUID());
        event.setAggregateType("BOOKING");
        event.setAggregateId(UUID.randomUUID());
        event.setEventType("BOOKING_CONFIRMED");
        event.setTopic("booking.events");
        event.setEventKey(UUID.randomUUID().toString());
        event.setPayload("{\"hello\":\"world\"}");
        event.setStatus(OutboxEventStatus.PENDING);
        event.setRetryCount(0);
        event.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        event.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1));
        return event;
    }
}
