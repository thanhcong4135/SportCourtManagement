package com.sportcourt.payment.scheduler;

import com.sportcourt.payment.domain.enums.OutboxEventStatus;
import com.sportcourt.payment.event.PaymentEventPublisher;
import com.sportcourt.payment.outbox.OutboxEvent;
import com.sportcourt.payment.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final int batchSize;
    private final int maxRetries;
    private final int initialRetryDelaySeconds;
    private final int maxRetryDelaySeconds;

    public OutboxPublisherScheduler(OutboxEventRepository outboxEventRepository,
                                    PaymentEventPublisher paymentEventPublisher,
                                    @Value("${outbox.publisher.batch-size:100}") int batchSize,
                                    @Value("${outbox.publisher.max-retries:10}") int maxRetries,
                                    @Value("${outbox.publisher.initial-retry-delay-seconds:5}") int initialRetryDelaySeconds,
                                    @Value("${outbox.publisher.max-retry-delay-seconds:300}") int maxRetryDelaySeconds) {
        this.outboxEventRepository = outboxEventRepository;
        this.paymentEventPublisher = paymentEventPublisher;
        this.batchSize = batchSize;
        this.maxRetries = maxRetries;
        this.initialRetryDelaySeconds = initialRetryDelaySeconds;
        this.maxRetryDelaySeconds = maxRetryDelaySeconds;
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
                paymentEventPublisher.publishRaw(
                    event.getTopic(),
                    event.getEventKey(),
                    event.getPayload(),
                    event.getId() != null ? event.getId().toString() : null
                );
                markAsSent(event);
            } catch (RuntimeException ex) {
                markForRetry(event, ex.getMessage());
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
            log.error("Payment outbox event {} moved to FAILED after {} retries", event.getId(), retry);
            return;
        }

        long delaySeconds = backoffSeconds(retry);
        event.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(delaySeconds));
        log.warn("Payment outbox event {} publish failed, retry {} in {}s", event.getId(), retry, delaySeconds);
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
