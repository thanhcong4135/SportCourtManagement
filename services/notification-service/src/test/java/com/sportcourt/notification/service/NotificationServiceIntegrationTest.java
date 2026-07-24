package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationChannel;
import com.sportcourt.notification.dto.NotificationResponse;
import com.sportcourt.notification.dto.NotificationSendRequest;
import com.sportcourt.notification.repository.NotificationMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class NotificationServiceIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private CustomerNotificationService customerNotificationService;

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
            "Thông báo",
            "/account",
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
            "Đặt sân thành công",
            "/account/bookings/" + bookingId,
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
            "Thông báo",
            "/account",
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
            "Thông báo",
            "/account",
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

    @Test
    void customerInbox_shouldEnforceOwnershipAndReadState() {
        UUID customerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();

        NotificationResponse queued = notificationService.queueFromEvent(new NotificationEventCommand(
            "booking.events",
            "evt-customer-inbox",
            "BOOKING_CONFIRMED",
            bookingId,
            null,
            customerId,
            "evt-customer-inbox",
            NotificationChannel.IN_APP,
            customerId.toString(),
            "BOOKING_CONFIRMED",
            "Đặt sân thành công",
            "/account/bookings/" + bookingId,
            "Booking của bạn đã được xác nhận.",
            Map.of("bookingId", bookingId.toString())
        ));
        notificationService.dispatchPending();

        assertThat(customerNotificationService.listMine(customerId, false, PageRequest.of(0, 20)).getContent())
            .extracting(NotificationResponse::id)
            .containsExactly(queued.id());
        assertThat(customerNotificationService.listMine(otherCustomerId, false, PageRequest.of(0, 20)))
            .isEmpty();
        assertThat(customerNotificationService.countUnread(customerId)).isEqualTo(1);

        NotificationResponse read = customerNotificationService.markRead(customerId, queued.id());
        NotificationResponse readAgain = customerNotificationService.markRead(customerId, queued.id());

        assertThat(read.unread()).isFalse();
        assertThat(read.readAt()).isNotNull();
        assertThat(readAgain.readAt()).isEqualTo(read.readAt());
        assertThat(customerNotificationService.countUnread(customerId)).isZero();
        assertThat(customerNotificationService.listMine(customerId, true, PageRequest.of(0, 20))).isEmpty();
        assertThatThrownBy(() -> customerNotificationService.markRead(otherCustomerId, queued.id()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void markAllRead_shouldOnlyUpdateCurrentCustomersSentInAppRows() {
        UUID customerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();

        queueSent(customerId, "evt-mark-all-1");
        queueSent(customerId, "evt-mark-all-2");
        queueSent(otherCustomerId, "evt-mark-all-other");

        assertThat(customerNotificationService.markAllRead(customerId)).isEqualTo(2);
        assertThat(customerNotificationService.countUnread(customerId)).isZero();
        assertThat(customerNotificationService.countUnread(otherCustomerId)).isEqualTo(1);
    }

    @Test
    void customerInbox_shouldHideFailedRows() {
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        notificationService.queueFromEvent(new NotificationEventCommand(
            "booking.events",
            "evt-failed-inbox",
            "BOOKING_CONFIRMED",
            bookingId,
            null,
            customerId,
            "evt-failed-inbox",
            NotificationChannel.IN_APP,
            "customer-fail",
            "BOOKING_CONFIRMED",
            "Đặt sân thành công",
            "/account/bookings/" + bookingId,
            "Booking của bạn đã được xác nhận.",
            Map.of()
        ));

        notificationService.dispatchPending();
        notificationService.dispatchPending();

        assertThat(customerNotificationService.listMine(customerId, false, PageRequest.of(0, 20))).isEmpty();
        assertThat(customerNotificationService.countUnread(customerId)).isZero();
    }

    @Test
    void sameEvent_shouldRemainIdempotentPerChannel() {
        UUID customerId = UUID.randomUUID();
        UUID bookingId = UUID.randomUUID();
        NotificationEventCommand inApp = eventCommand(
            "evt-multi-channel",
            bookingId,
            customerId,
            NotificationChannel.IN_APP,
            customerId.toString()
        );
        NotificationEventCommand email = eventCommand(
            "evt-multi-channel",
            bookingId,
            customerId,
            NotificationChannel.EMAIL,
            "customer@example.com"
        );

        NotificationResponse firstInApp = notificationService.queueFromEvent(inApp);
        NotificationResponse firstEmail = notificationService.queueFromEvent(email);
        NotificationResponse replayedInApp = notificationService.queueFromEvent(inApp);
        NotificationResponse replayedEmail = notificationService.queueFromEvent(email);

        assertThat(firstInApp.id()).isEqualTo(replayedInApp.id());
        assertThat(firstEmail.id()).isEqualTo(replayedEmail.id());
        assertThat(firstInApp.id()).isNotEqualTo(firstEmail.id());
        assertThat(notificationMessageRepository.count()).isEqualTo(2);
    }

    @Test
    void missingEmailSender_shouldFailNormallyAndAllowManualRetry() {
        NotificationResponse queued = notificationService.send(new NotificationSendRequest(
            "customer@example.com",
            NotificationChannel.EMAIL,
            "BOOKING_CONFIRMED",
            "Đặt sân thành công",
            "/account",
            "Booking của bạn đã được xác nhận.",
            Map.of()
        ));

        notificationService.dispatchPending();
        notificationService.dispatchPending();

        NotificationResponse failed = notificationService.getById(queued.id());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.lastError()).contains("No notification sender configured for channel EMAIL");

        NotificationResponse retried = notificationService.retry(queued.id());
        assertThat(retried.status()).isEqualTo("QUEUED");
        assertThat(retried.lastError()).isNull();
    }

    private void queueSent(UUID customerId, String eventId) {
        UUID bookingId = UUID.randomUUID();
        notificationService.queueFromEvent(new NotificationEventCommand(
            "booking.events",
            eventId,
            "BOOKING_CONFIRMED",
            bookingId,
            null,
            customerId,
            eventId,
            NotificationChannel.IN_APP,
            customerId.toString(),
            "BOOKING_CONFIRMED",
            "Đặt sân thành công",
            "/account/bookings/" + bookingId,
            "Booking của bạn đã được xác nhận.",
            Map.of()
        ));
        notificationService.dispatchPending();
    }

    private NotificationEventCommand eventCommand(String eventId,
                                                  UUID bookingId,
                                                  UUID customerId,
                                                  NotificationChannel channel,
                                                  String recipient) {
        return new NotificationEventCommand(
            "booking.events",
            eventId,
            "BOOKING_CONFIRMED",
            bookingId,
            null,
            customerId,
            eventId,
            channel,
            recipient,
            "BOOKING_CONFIRMED",
            "Đặt sân thành công",
            "/account/bookings/" + bookingId,
            "Booking của bạn đã được xác nhận.",
            Map.of()
        );
    }
}
