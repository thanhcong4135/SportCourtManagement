package com.sportcourt.payment.monitoring;

import com.sportcourt.payment.domain.enums.OutboxEventStatus;
import com.sportcourt.payment.outbox.OutboxEvent;
import com.sportcourt.payment.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Component("outbox")
public class OutboxHealthIndicator implements HealthIndicator {

    private final OutboxEventRepository outboxEventRepository;
    private final long failedThreshold;

    public OutboxHealthIndicator(
        OutboxEventRepository outboxEventRepository,
        @Value("${outbox.monitoring.failed-threshold:10}") long failedThreshold
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.failedThreshold = failedThreshold;
    }

    @Override
    public Health health() {
        long pendingCount = outboxEventRepository.countByStatus(OutboxEventStatus.PENDING);
        long failedCount = outboxEventRepository.countByStatus(OutboxEventStatus.FAILED);
        long oldestPendingAgeSeconds = oldestPendingAgeSeconds();

        Health.Builder builder = failedCount >= failedThreshold
            ? Health.down()
            : Health.up();

        return builder
            .withDetail("pendingCount", pendingCount)
            .withDetail("failedCount", failedCount)
            .withDetail("failedThreshold", failedThreshold)
            .withDetail("oldestPendingAgeSeconds", oldestPendingAgeSeconds)
            .build();
    }

    private long oldestPendingAgeSeconds() {
        Optional<OutboxEvent> oldestPending = outboxEventRepository.findFirstByStatusOrderByCreatedAtAsc(
            OutboxEventStatus.PENDING
        );
        if (oldestPending.isEmpty()) {
            return 0;
        }
        OffsetDateTime createdAt = oldestPending.get().getCreatedAt();
        return Math.max(0, Duration.between(createdAt, OffsetDateTime.now(ZoneOffset.UTC)).getSeconds());
    }
}
