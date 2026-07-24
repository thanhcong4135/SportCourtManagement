package com.sportcourt.notification.service;

import com.sportcourt.notification.domain.NotificationChannel;
import com.sportcourt.notification.domain.NotificationMessage;
import com.sportcourt.notification.domain.enums.NotificationStatus;
import com.sportcourt.notification.dto.NotificationResponse;
import com.sportcourt.notification.repository.NotificationMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class CustomerNotificationService {

    private final NotificationMessageRepository notificationMessageRepository;
    private final NotificationResponseMapper responseMapper;

    public CustomerNotificationService(NotificationMessageRepository notificationMessageRepository,
                                       NotificationResponseMapper responseMapper) {
        this.notificationMessageRepository = notificationMessageRepository;
        this.responseMapper = responseMapper;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listMine(UUID customerId, boolean unreadOnly, Pageable pageable) {
        Page<NotificationMessage> page = unreadOnly
            ? notificationMessageRepository
                .findByCustomerIdAndChannelAndStatusAndReadAtIsNullOrderByCreatedAtDesc(
                    customerId,
                    NotificationChannel.IN_APP,
                    NotificationStatus.SENT,
                    pageable
                )
            : notificationMessageRepository.findByCustomerIdAndChannelAndStatusOrderByCreatedAtDesc(
                customerId,
                NotificationChannel.IN_APP,
                NotificationStatus.SENT,
                pageable
            );
        return page.map(responseMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID customerId) {
        return notificationMessageRepository.countByCustomerIdAndChannelAndStatusAndReadAtIsNull(
            customerId,
            NotificationChannel.IN_APP,
            NotificationStatus.SENT
        );
    }

    @Transactional
    public NotificationResponse markRead(UUID customerId, UUID notificationId) {
        NotificationMessage message = notificationMessageRepository.findByIdAndCustomerIdAndChannelAndStatus(
                notificationId,
                customerId,
                NotificationChannel.IN_APP,
                NotificationStatus.SENT
            )
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (message.getReadAt() == null) {
            OffsetDateTime now = utcNow();
            message.setReadAt(now);
            message.setUpdatedAt(now);
            message = notificationMessageRepository.save(message);
        }
        return responseMapper.toResponse(message);
    }

    @Transactional
    public long markAllRead(UUID customerId) {
        OffsetDateTime now = utcNow();
        return notificationMessageRepository.markAllRead(
            customerId,
            NotificationChannel.IN_APP,
            NotificationStatus.SENT,
            now
        );
    }

    private OffsetDateTime utcNow() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }
}
