package com.sportcourt.payment.monitoring;

import com.sportcourt.payment.domain.enums.OutboxEventStatus;
import com.sportcourt.payment.outbox.OutboxEvent;
import com.sportcourt.payment.repository.OutboxEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxHealthIndicatorTest {

    @Test
    void health_shouldBeUpWhenFailedCountBelowThreshold() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(2L);
        when(repository.countByStatus(OutboxEventStatus.FAILED)).thenReturn(1L);
        when(repository.findFirstByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
            .thenReturn(Optional.empty());
        OutboxHealthIndicator indicator = new OutboxHealthIndicator(repository, 3);

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails()).containsEntry("pendingCount", 2L);
        assertThat(health.getDetails()).containsEntry("failedCount", 1L);
    }

    @Test
    void health_shouldBeDownWhenFailedCountReachesThreshold() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        when(repository.countByStatus(OutboxEventStatus.PENDING)).thenReturn(1L);
        when(repository.countByStatus(OutboxEventStatus.FAILED)).thenReturn(5L);

        OutboxEvent pending = new OutboxEvent();
        pending.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(2));
        when(repository.findFirstByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING))
            .thenReturn(Optional.of(pending));
        OutboxHealthIndicator indicator = new OutboxHealthIndicator(repository, 5);

        Health health = indicator.health();

        assertThat(health.getStatus().getCode()).isEqualTo("DOWN");
        assertThat(health.getDetails()).containsEntry("failedCount", 5L);
        assertThat((Long) health.getDetails().get("oldestPendingAgeSeconds")).isGreaterThan(0L);
    }
}
