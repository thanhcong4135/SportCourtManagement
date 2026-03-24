package com.sportcourt.core.scheduler;

import com.sportcourt.core.domain.OutboxEvent;
import com.sportcourt.core.domain.enums.OutboxEventStatus;
import com.sportcourt.core.event.BookingEventPublisher;
import com.sportcourt.core.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class OutboxPublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final BookingEventPublisher bookingEventPublisher;
    private final int batchSize;
    private final int maxRetries;
    private final int initialRetryDelaySeconds;
    private final int maxRetryDelaySeconds;
    private final Counter publishSuccessCounter;
    private final Counter publishFailureCounter;

    public OutboxPublisherScheduler(OutboxEventRepository outboxEventRepository,
                                    BookingEventPublisher bookingEventPublisher,
                                    MeterRegistry meterRegistry,
                                    @Value("${outbox.publisher.batch-size:100}") int batchSize,
                                    @Value("${outbox.publisher.max-retries:10}") int maxRetries,
                                    @Value("${outbox.publisher.initial-retry-delay-seconds:5}") int initialRetryDelaySeconds,
                                    @Value("${outbox.publisher.max-retry-delay-seconds:300}") int maxRetryDelaySeconds) {
        this.outboxEventRepository = outboxEventRepository;
        this.bookingEventPublisher = bookingEventPublisher;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.initialRetryDelaySeconds = initialRetryDelaySeconds;
        this.maxRetryDelaySeconds = maxRetryDelaySeconds;
        this.publishSuccessCounter = Counter.builder("outbox.publish.success")
            .description("Number of outbox events published successfully")
            .register(meterRegistry);
        this.publishFailureCounter = Counter.builder("outbox.publish.failure")
            .description("Number of outbox publish failures")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${outbox.publisher.fixed-delay-ms:5000}")
    @Transactional
    public void publishPendingEvents() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<OutboxEvent> events = outboxEventRepository.findBatchForPublish(
            OutboxEventStatus.PENDING,
            now,
            PageRequest.of(0, batchSize)
        );

        for (OutboxEvent event : events) {
            try {
                bookingEventPublisher.publishRaw(
                    event.getTopic(),
                    event.getEventKey(),
                    event.getPayload(),
                    event.getId() != null ? event.getId().toString() : null
                );
                markAsSent(event);
                publishSuccessCounter.increment();
            } catch (RuntimeException ex) {
                markForRetry(event, ex.getMessage());
                publishFailureCounter.increment();
            }
        }
    }

    private void markAsSent(OutboxEvent event) {
        event.setStatus(OutboxEventStatus.SENT);
        event.setSentAt(OffsetDateTime.now(ZoneOffset.UTC));
        event.setLastError(null);
        event.setNextAttemptAt(null);
    }

    private void markForRetry(OutboxEvent event, String error) {
        int retry = event.getRetryCount() + 1;
        event.setRetryCount(retry);
        event.setLastError(truncateError(error));
        if (retry >= maxRetries) {
            event.setStatus(OutboxEventStatus.FAILED);
            event.setNextAttemptAt(null);
            log.error("Outbox event {} moved to FAILED after {} retries", event.getId(), retry);
            return;
        }

        long delaySeconds = backoffSeconds(retry);
        event.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delaySeconds));
        log.warn("Outbox event {} publish failed, retry {} in {}s", event.getId(), retry, delaySeconds);
    }

    private long backoffSeconds(int retryCount) {
        long raw = (long) initialRetryDelaySeconds << Math.max(0, retryCount - 1);
        return Math.min(raw, maxRetryDelaySeconds);
    }

    private String truncateError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }
}
