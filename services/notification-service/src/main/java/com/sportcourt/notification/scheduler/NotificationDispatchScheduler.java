package com.sportcourt.notification.scheduler;

import com.sportcourt.notification.service.NotificationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationDispatchScheduler {

    private final NotificationService notificationService;

    public NotificationDispatchScheduler(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Scheduled(fixedDelayString = "${notification.dispatcher.fixed-delay-ms:5000}")
    public void dispatchPendingNotifications() {
        notificationService.dispatchPending();
    }
}
