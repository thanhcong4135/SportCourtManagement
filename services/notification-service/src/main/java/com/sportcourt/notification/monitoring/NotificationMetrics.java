package com.sportcourt.notification.monitoring;

import com.sportcourt.notification.domain.enums.NotificationStatus;
import com.sportcourt.notification.repository.NotificationMessageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private final Counter dispatchSentCounter;
    private final Counter dispatchRetryCounter;
    private final Counter dispatchFailedCounter;

    public NotificationMetrics(NotificationMessageRepository notificationMessageRepository,
                               MeterRegistry meterRegistry) {
        this.dispatchSentCounter = Counter.builder("notification.dispatch.sent")
            .description("Total notifications sent successfully")
            .register(meterRegistry);
        this.dispatchRetryCounter = Counter.builder("notification.dispatch.retry")
            .description("Total notification retries")
            .register(meterRegistry);
        this.dispatchFailedCounter = Counter.builder("notification.dispatch.failed")
            .description("Total notifications moved to failed state")
            .register(meterRegistry);

        Gauge.builder("notification.queue.pending", notificationMessageRepository,
                repo -> repo.countByStatus(NotificationStatus.QUEUED))
            .description("Queued notifications waiting for dispatch")
            .register(meterRegistry);

        Gauge.builder("notification.queue.failed", notificationMessageRepository,
                repo -> repo.countByStatus(NotificationStatus.FAILED))
            .description("Notifications in failed dead-letter bucket")
            .register(meterRegistry);
    }

    public void recordSent() {
        dispatchSentCounter.increment();
    }

    public void recordRetry() {
        dispatchRetryCounter.increment();
    }

    public void recordFailed() {
        dispatchFailedCounter.increment();
    }
}
