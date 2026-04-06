package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationChannel;
import com.sportcourt.notification.dto.NotificationResponse;
import com.sportcourt.notification.dto.NotificationSendRequest;
import com.sportcourt.notification.repository.NotificationMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationMessageRepository notificationMessageRepository;

    @BeforeEach
    void setUp() {
        notificationMessageRepository.deleteAll();
    }

    @Test
    void send_shouldQueueNotification() {
        NotificationResponse response = notificationService.send(new NotificationSendRequest(
            "customer-1",
            NotificationChannel.IN_APP,
            "MANUAL_NOTICE",
            "Hello",
            Map.of("source", "manual")
        ));

        assertThat(response.id()).isNotNull();
        assertThat(response.status()).isEqualTo("QUEUED");
        assertThat(response.retryCount()).isEqualTo(0);
    }

    @Test
    void queueFromEvent_shouldBeIdempotentBySourceEventAndRecipient() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        NotificationEventCommand command = new NotificationEventCommand(
            "booking.events",
            "evt-1",
            "BOOKING_CONFIRMED",
            bookingId,
            null,
            customerId,
            "evt-1",
            NotificationChannel.IN_APP,
            customerId.toString(),
            "BOOKING_CONFIRMED",
            "Booking confirmed",
            Map.of("bookingId", bookingId.toString())
        );

        NotificationResponse first = notificationService.queueFromEvent(command);
        NotificationResponse second = notificationService.queueFromEvent(command);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(notificationMessageRepository.count()).isEqualTo(1);
    }

    @Test
    void dispatchPending_shouldMoveQueuedToSent() {
        NotificationResponse queued = notificationService.send(new NotificationSendRequest(
            "customer-2",
            NotificationChannel.IN_APP,
            "MANUAL_NOTICE",
            "Hello",
            Map.of()
        ));

        notificationService.dispatchPending();

        NotificationResponse sent = notificationService.getById(queued.id());
        assertThat(sent.status()).isEqualTo("SENT");
        assertThat(sent.sentAt()).isNotNull();
    }

    @Test
    void dispatchPending_shouldMoveToFailedAfterMaxAttempts() {
        NotificationResponse queued = notificationService.send(new NotificationSendRequest(
            "customer-fail-1",
            NotificationChannel.IN_APP,
            "MANUAL_NOTICE",
            "Hello",
            Map.of()
        ));

        notificationService.dispatchPending();
        notificationService.dispatchPending();

        NotificationResponse failed = notificationService.getById(queued.id());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.retryCount()).isEqualTo(2);
        assertThat(failed.lastError()).isNotBlank();
    }
}
