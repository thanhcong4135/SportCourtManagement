package com.sportcourt.core.dlq;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "dead_letter_event")
@Getter
@Setter
public class DeadLetterEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "source_topic", nullable = false, length = 255)
    private String sourceTopic;

    @Column(name = "dead_letter_topic", nullable = false, length = 255)
    private String deadLetterTopic;

    @Column(name = "kafka_partition", nullable = false)
    private int kafkaPartition;

    @Column(name = "kafka_offset", nullable = false)
    private long kafkaOffset;

    @Column(name = "event_key", length = 255)
    private String eventKey;

    @Column(name = "event_id", length = 128)
    private String eventId;

    @Lob
    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "failure_reason", length = 1024)
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private DeadLetterEventStatus status;

    @Column(name = "replay_count", nullable = false)
    private int replayCount;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt;

    @Column(name = "last_replayed_at")
    private OffsetDateTime lastReplayedAt;
}
