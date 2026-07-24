package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationChannel;
import com.sportcourt.notification.domain.NotificationMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationSenderRouterTest {

    @Test
    void routesToSenderMatchingChannel() {
        AtomicInteger inAppCalls = new AtomicInteger();
        AtomicInteger emailCalls = new AtomicInteger();
        NotificationSenderRouter router = new NotificationSenderRouter(List.of(
            sender(NotificationChannel.IN_APP, inAppCalls),
            sender(NotificationChannel.EMAIL, emailCalls)
        ));
        NotificationMessage message = new NotificationMessage();
        message.setChannel(NotificationChannel.EMAIL);

        router.send(message);

        assertThat(inAppCalls).hasValue(0);
        assertThat(emailCalls).hasValue(1);
    }

    @Test
    void missingSenderThrowsInsteadOfSilentlyDelivering() {
        NotificationSenderRouter router = new NotificationSenderRouter(List.of());
        NotificationMessage message = new NotificationMessage();
        message.setChannel(NotificationChannel.EMAIL);

        assertThatThrownBy(() -> router.send(message))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("EMAIL");
    }

    private NotificationChannelSender sender(NotificationChannel channel, AtomicInteger calls) {
        return new NotificationChannelSender() {
            @Override
            public NotificationChannel channel() {
                return channel;
            }

            @Override
            public void send(NotificationMessage notificationMessage) {
                calls.incrementAndGet();
            }
        };
    }
}
