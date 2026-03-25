package com.sportcourt.payment.monitoring;

import com.sportcourt.payment.domain.enums.OutboxEventStatus;
import com.sportcourt.payment.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class OutboxMetrics {

    public OutboxMetrics(MeterRegistry meterRegistry, OutboxEventRepository outboxEventRepository) {
        Gauge.builder("outbox.events.pending", outboxEventRepository, r -> r.countByStatus(OutboxEventStatus.PENDING))
            .description("Number of pending outbox events")
            .register(meterRegistry);

        Gauge.builder("outbox.events.failed", outboxEventRepository, r -> r.countByStatus(OutboxEventStatus.FAILED))
            .description("Number of failed outbox events")
            .register(meterRegistry);
    }
}
