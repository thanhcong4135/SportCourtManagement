package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MockNotificationSender implements NotificationSender {

    private final String failRecipientPattern;

    public MockNotificationSender(@Value("${notification.delivery.mock.fail-recipient-pattern:}") String failRecipientPattern) {
        this.failRecipientPattern = failRecipientPattern == null ? "" : failRecipientPattern.trim();
    }

    @Override
    public void send(NotificationMessage notificationMessage) {
        if (!failRecipientPattern.isBlank() &&
            notificationMessage.getRecipient() != null &&
            notificationMessage.getRecipient().contains(failRecipientPattern)) {
            throw new IllegalStateException("Mock delivery failure for recipient pattern: " + failRecipientPattern);
        }
    }
}
