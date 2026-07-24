package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationChannel;
import com.sportcourt.notification.domain.NotificationMessage;

public interface NotificationChannelSender {

    NotificationChannel channel();

    void send(NotificationMessage notificationMessage);
}
