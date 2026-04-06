package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationMessage;

public interface NotificationSender {

    void send(NotificationMessage notificationMessage);
}
