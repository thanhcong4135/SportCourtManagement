package com.sportcourt.core.dto;

import com.sportcourt.core.dlq.DeadLetterEventStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record DeadLetterEventResponse(
    UUID id,
    String sourceTopic,
    String deadLetterTopic,
    int kafkaPartition,
    long kafkaOffset,
    String eventKey,
    String eventId,
    String failureReason,
    DeadLetterEventStatus status,
    int replayCount,
    OffsetDateTime receivedAt,
    OffsetDateTime lastReplayedAt
) {
}
