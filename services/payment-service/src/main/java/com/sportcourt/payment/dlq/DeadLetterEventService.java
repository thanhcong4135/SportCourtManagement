package com.sportcourt.payment.dlq;

import com.sportcourt.payment.dto.DeadLetterEventResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

@Service
public class DeadLetterEventService {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterEventService.class);

    private final DeadLetterEventRepository deadLetterEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String dlqSuffix;
    private final int maxReplayAttempts;

    public DeadLetterEventService(DeadLetterEventRepository deadLetterEventRepository,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  @Value("${kafka.consumer.dlq.suffix:.dlq}") String dlqSuffix,
                                  @Value("${kafka.dlq.replay.max-attempts:5}") int maxReplayAttempts) {
        this.deadLetterEventRepository = deadLetterEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.dlqSuffix = dlqSuffix;
        this.maxReplayAttempts = maxReplayAttempts;
    }

    @Transactional
    public void capture(String deadLetterTopic,
                        int kafkaPartition,
                        long kafkaOffset,
                        String payload,
                        Map<String, Object> headers) {
        if (deadLetterEventRepository.existsByDeadLetterTopicAndKafkaPartitionAndKafkaOffset(
            deadLetterTopic,
            kafkaPartition,
            kafkaOffset
        )) {
            return;
        }

        String originalTopic = resolveSourceTopic(deadLetterTopic, headers);
        DeadLetterEvent event = new DeadLetterEvent();
        event.setSourceTopic(originalTopic);
        event.setDeadLetterTopic(deadLetterTopic);
        event.setKafkaPartition(kafkaPartition);
        event.setKafkaOffset(kafkaOffset);
        event.setEventKey(asString(headers.get(KafkaHeaders.RECEIVED_KEY)));
        event.setEventId(asString(headers.get("event-id")));
        event.setPayload(payload);
        event.setFailureReason(extractFailureReason(headers));
        event.setStatus(DeadLetterEventStatus.RECEIVED);
        event.setReplayCount(0);
        event.setReceivedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            deadLetterEventRepository.save(event);
            log.warn("Captured DLQ event {}:{}:{} -> {}", deadLetterTopic, kafkaPartition, kafkaOffset, originalTopic);
        } catch (DataIntegrityViolationException ex) {
            log.debug("DLQ event already captured {}:{}:{}", deadLetterTopic, kafkaPartition, kafkaOffset);
        }
    }

    @Transactional(readOnly = true)
    public Page<DeadLetterEventResponse> list(DeadLetterEventStatus status, Pageable pageable) {
        Page<DeadLetterEvent> page = status == null
            ? deadLetterEventRepository.findAllByOrderByReceivedAtDesc(pageable)
            : deadLetterEventRepository.findByStatusOrderByReceivedAtDesc(status, pageable);
        return page.map(this::toResponse);
    }

    @Transactional
    public DeadLetterEventResponse replay(UUID id) {
        DeadLetterEvent event = deadLetterEventRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dead-letter event not found"));

        if (event.getReplayCount() >= maxReplayAttempts && event.getStatus() != DeadLetterEventStatus.REPLAYED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Replay attempt limit exceeded");
        }
        if (event.getSourceTopic() == null || event.getSourceTopic().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Dead-letter event missing source topic");
        }

        OffsetDateTime replayTime = OffsetDateTime.now(ZoneOffset.UTC);
        try {
            MessageBuilder<String> builder = MessageBuilder.withPayload(event.getPayload())
                .setHeader(KafkaHeaders.TOPIC, event.getSourceTopic())
                .setHeader("x-replay-source", "dlq")
                .setHeader("x-replay-at", replayTime.toString());
            if (event.getEventKey() != null && !event.getEventKey().isBlank()) {
                builder.setHeader(KafkaHeaders.KEY, event.getEventKey());
            }
            if (event.getEventId() != null && !event.getEventId().isBlank()) {
                builder.setHeader("event-id", event.getEventId());
            }

            kafkaTemplate.send(builder.build()).join();
            event.setStatus(DeadLetterEventStatus.REPLAYED);
            event.setFailureReason(null);
            log.info("Replayed DLQ event {} to topic {}", event.getId(), event.getSourceTopic());
        } catch (RuntimeException ex) {
            event.setStatus(DeadLetterEventStatus.FAILED);
            event.setFailureReason(truncate(extractRootMessage(ex)));
            log.error("Failed to replay DLQ event {}", event.getId(), ex);
        }

        event.setReplayCount(event.getReplayCount() + 1);
        event.setLastReplayedAt(replayTime);
        DeadLetterEvent saved = deadLetterEventRepository.save(event);
        return toResponse(saved);
    }

    private String resolveSourceTopic(String deadLetterTopic, Map<String, Object> headers) {
        String headerTopic = asString(headers.get(KafkaHeaders.DLT_ORIGINAL_TOPIC));
        if (headerTopic != null && !headerTopic.isBlank()) {
            return headerTopic;
        }
        if (deadLetterTopic != null && deadLetterTopic.endsWith(dlqSuffix)) {
            return deadLetterTopic.substring(0, deadLetterTopic.length() - dlqSuffix.length());
        }
        return deadLetterTopic;
    }

    private String extractFailureReason(Map<String, Object> headers) {
        String message = asString(headers.get(KafkaHeaders.DLT_EXCEPTION_MESSAGE));
        String exceptionType = asString(headers.get(KafkaHeaders.DLT_EXCEPTION_FQCN));

        String reason;
        if (exceptionType != null && !exceptionType.isBlank() && message != null && !message.isBlank()) {
            reason = exceptionType + ": " + message;
        } else {
            reason = message;
        }
        return truncate(reason);
    }

    private String extractRootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value.toString();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 1024 ? value : value.substring(0, 1024);
    }

    private DeadLetterEventResponse toResponse(DeadLetterEvent event) {
        return new DeadLetterEventResponse(
            event.getId(),
            event.getSourceTopic(),
            event.getDeadLetterTopic(),
            event.getKafkaPartition(),
            event.getKafkaOffset(),
            event.getEventKey(),
            event.getEventId(),
            event.getFailureReason(),
            event.getStatus(),
            event.getReplayCount(),
            event.getReceivedAt(),
            event.getLastReplayedAt()
        );
    }
}
