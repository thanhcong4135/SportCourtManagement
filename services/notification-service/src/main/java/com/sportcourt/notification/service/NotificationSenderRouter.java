package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationChannel;
import com.sportcourt.notification.domain.NotificationMessage;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class NotificationSenderRouter implements NotificationSender {

    private final Map<NotificationChannel, NotificationChannelSender> senders;

    public NotificationSenderRouter(List<NotificationChannelSender> channelSenders) {
        Map<NotificationChannel, NotificationChannelSender> mappedSenders = new EnumMap<>(NotificationChannel.class);
        for (NotificationChannelSender sender : channelSenders) {
            NotificationChannelSender duplicate = mappedSenders.put(sender.channel(), sender);
            if (duplicate != null) {
                throw new IllegalStateException("Multiple notification senders configured for " + sender.channel());
            }
        }
        this.senders = Map.copyOf(mappedSenders);
    }

    @Override
    public void send(NotificationMessage notificationMessage) {
        NotificationChannelSender sender = senders.get(notificationMessage.getChannel());
        if (sender == null) {
            throw new IllegalStateException(
                "No notification sender configured for channel " + notificationMessage.getChannel()
            );
        }
        sender.send(notificationMessage);
    }
}
