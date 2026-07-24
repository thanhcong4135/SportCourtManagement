package com.sportcourt.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.notification.domain.NotificationChannel;
import com.sportcourt.notification.domain.NotificationMessage;
import com.sportcourt.notification.domain.enums.NotificationStatus;
import com.sportcourt.notification.dto.NotificationResponse;
import com.sportcourt.notification.dto.NotificationSendRequest;
import com.sportcourt.notification.monitoring.NotificationMetrics;
import com.sportcourt.notification.repository.NotificationMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationMessageRepository notificationMessageRepository;
    private final NotificationSender notificationSender;
    private final NotificationMetrics notificationMetrics;
    private final ObjectMapper objectMapper;
    private final NotificationResponseMapper responseMapper;
    private final int dispatcherBatchSize;
    private final int dispatcherMaxAttempts;
    private final long initialBackoffMs;
    private final long maxBackoffMs;

    public NotificationService(NotificationMessageRepository notificationMessageRepository,
                               NotificationSender notificationSender,
                               NotificationMetrics notificationMetrics,
                               ObjectMapper objectMapper,
                               NotificationResponseMapper responseMapper,
                               @Value("${notification.dispatcher.batch-size:100}") int dispatcherBatchSize,
                               @Value("${notification.dispatcher.max-attempts:5}") int dispatcherMaxAttempts,
                               @Value("${notification.dispatcher.initial-backoff-ms:5000}") long initialBackoffMs,
                               @Value("${notification.dispatcher.max-backoff-ms:300000}") long maxBackoffMs) {
        this.notificationMessageRepository = notificationMessageRepository;
        this.notificationSender = notificationSender;
        this.notificationMetrics = notificationMetrics;
        this.objectMapper = objectMapper;
        this.responseMapper = responseMapper;
        this.dispatcherBatchSize = dispatcherBatchSize;
        this.dispatcherMaxAttempts = dispatcherMaxAttempts;
        this.initialBackoffMs = initialBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    @Transactional
    public NotificationResponse send(NotificationSendRequest request) {
        NotificationMessage message = new NotificationMessage();
        OffsetDateTime now = utcNow();
        message.setStatus(NotificationStatus.QUEUED);
        message.setChannel(request.channel());
        message.setRecipient(request.recipient().trim());
        message.setTemplateCode(normalizeTemplateCode(request.templateCode()));
        message.setTitle(request.title().trim());
        message.setDeepLink(normalizeDeepLink(request.deepLink()));
        message.setMessage(request.message().trim());
        message.setMetadataJson(toMetadataJson(request.metadata()));
        message.setRetryCount(0);
        message.setNextAttemptAt(now);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);
        return responseMapper.toResponse(notificationMessageRepository.save(message));
    }

    @Transactional
    public NotificationResponse queueFromEvent(NotificationEventCommand command) {
        validateEventCommand(command);

        NotificationMessage existing = notificationMessageRepository
            .findBySourceEventIdAndChannelAndRecipientAndTemplateCode(
                command.sourceEventId(),
                command.channel(),
                command.recipient(),
                command.templateCode()
            )
            .orElse(null);
        if (existing != null) {
            return responseMapper.toResponse(existing);
        }

        OffsetDateTime now = utcNow();
        NotificationMessage message = new NotificationMessage();
        message.setStatus(NotificationStatus.QUEUED);
        message.setChannel(command.channel());
        message.setRecipient(command.recipient().trim());
        message.setTemplateCode(command.templateCode());
        message.setTitle(command.title().trim());
        message.setDeepLink(normalizeDeepLink(command.deepLink()));
        message.setMessage(command.message());
        message.setMetadataJson(toMetadataJson(command.metadata()));
        message.setSourceTopic(command.sourceTopic());
        message.setSourceEventId(command.sourceEventId());
        message.setSourceEventType(command.sourceEventType());
        message.setBookingId(command.bookingId());
        message.setPaymentId(command.paymentId());
        message.setCustomerId(command.customerId());
        message.setTraceId(command.traceId());
        message.setRetryCount(0);
        message.setNextAttemptAt(now);
        message.setCreatedAt(now);
        message.setUpdatedAt(now);

        try {
            return responseMapper.toResponse(notificationMessageRepository.save(message));
        } catch (DataIntegrityViolationException ex) {
            NotificationMessage replayed = notificationMessageRepository
                .findBySourceEventIdAndChannelAndRecipientAndTemplateCode(
                    command.sourceEventId(),
                    command.channel(),
                    command.recipient(),
                    command.templateCode()
                )
                .orElseThrow(() -> ex);
            return responseMapper.toResponse(replayed);
        }
    }

    @Transactional(readOnly = true)
    public NotificationResponse getById(UUID notificationId) {
        NotificationMessage message = notificationMessageRepository.findById(notificationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        return responseMapper.toResponse(message);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(UUID bookingId,
                                           UUID customerId,
                                           NotificationStatus status,
                                           Pageable pageable) {
        if (bookingId != null) {
            return notificationMessageRepository.findByBookingIdOrderByCreatedAtDesc(bookingId, pageable)
                .map(responseMapper::toResponse);
        }
        if (customerId != null) {
            return notificationMessageRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(responseMapper::toResponse);
        }

        if (status != null) {
            return notificationMessageRepository.findAll(
                (root, query, builder) -> builder.equal(root.get("status"), status),
                pageable
            ).map(responseMapper::toResponse);
        }

        return notificationMessageRepository.findAll(pageable).map(responseMapper::toResponse);
    }

    @Transactional
    public NotificationResponse retry(UUID notificationId) {
        NotificationMessage message = notificationMessageRepository.findById(notificationId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (message.getStatus() != NotificationStatus.FAILED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only FAILED notifications can be retried");
        }

        OffsetDateTime now = utcNow();
        message.setStatus(NotificationStatus.QUEUED);
        message.setLastError(null);
        message.setNextAttemptAt(now);
        message.setUpdatedAt(now);
        return responseMapper.toResponse(notificationMessageRepository.save(message));
    }

    @Transactional
    public void dispatchPending() {
        OffsetDateTime now = utcNow();
        List<NotificationMessage> batch = notificationMessageRepository.findBatchForDispatch(
            List.of(NotificationStatus.QUEUED),
            now,
            Pageable.ofSize(dispatcherBatchSize)
        );

        for (NotificationMessage message : batch) {
            dispatchSingle(message, now);
        }
    }

    private void dispatchSingle(NotificationMessage message, OffsetDateTime now) {
        try {
            notificationSender.send(message);
            message.setStatus(NotificationStatus.SENT);
            message.setSentAt(now);
            message.setLastError(null);
            message.setNextAttemptAt(now);
            message.setLastAttemptAt(now);
            message.setUpdatedAt(now);
            notificationMetrics.recordSent();
            return;
        } catch (RuntimeException ex) {
            message.setLastAttemptAt(now);
            message.setUpdatedAt(now);
            message.setLastError(truncate(ex.getMessage()));
        }

        int attempts = message.getRetryCount() + 1;
        message.setRetryCount(attempts);
        if (attempts >= dispatcherMaxAttempts) {
            message.setStatus(NotificationStatus.FAILED);
            message.setNextAttemptAt(now);
            notificationMetrics.recordFailed();
            return;
        }

        long delayMs = backoffMs(attempts);
        message.setStatus(NotificationStatus.QUEUED);
        message.setNextAttemptAt(now.plusNanos(delayMs * 1_000_000));
        notificationMetrics.recordRetry();
    }

    private long backoffMs(int attemptCount) {
        long raw = initialBackoffMs << Math.max(0, attemptCount - 1);
        return Math.min(raw, maxBackoffMs);
    }

    private String normalizeTemplateCode(String templateCode) {
        if (templateCode == null || templateCode.isBlank()) {
            return null;
        }
        return templateCode.trim();
    }

    private String normalizeDeepLink(String deepLink) {
        if (deepLink == null || deepLink.isBlank()) {
            return null;
        }
        String normalized = deepLink.trim();
        if (!normalized.startsWith("/") || normalized.startsWith("//") || normalized.contains("://")) {
            throw new IllegalArgumentException("deepLink must be an internal relative path");
        }
        return normalized;
    }

    private void validateEventCommand(NotificationEventCommand command) {
        if (command.sourceEventId() == null || command.sourceEventId().isBlank()) {
            throw new IllegalArgumentException("Event notification requires sourceEventId");
        }
        if (command.channel() == null) {
            throw new IllegalArgumentException("Event notification requires channel");
        }
        if (command.recipient() == null || command.recipient().isBlank()) {
            throw new IllegalArgumentException("Event notification requires recipient");
        }
        if (command.templateCode() == null || command.templateCode().isBlank()) {
            throw new IllegalArgumentException("Event notification requires templateCode");
        }
        if (command.title() == null || command.title().isBlank() || command.title().length() > 180) {
            throw new IllegalArgumentException("Event notification requires a title up to 180 characters");
        }
        normalizeDeepLink(command.deepLink());
        if (command.message() == null || command.message().isBlank()) {
            throw new IllegalArgumentException("Event notification requires message");
        }
    }

    private String toMetadataJson(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize notification metadata", ex);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1024 ? message : message.substring(0, 1024);
    }

    private OffsetDateTime utcNow() {
        return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

}
