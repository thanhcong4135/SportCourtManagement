package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationChannel;
import com.sportcourt.notification.domain.NotificationMessage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class InAppNotificationSender implements NotificationChannelSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void send(NotificationMessage notificationMessage) {
        // Persistence is the delivery mechanism for IN_APP notifications.
    }
}
