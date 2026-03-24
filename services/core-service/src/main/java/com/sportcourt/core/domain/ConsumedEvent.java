package com.sportcourt.core.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "consumed_event")
@Getter
@Setter
public class ConsumedEvent {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "event_id", nullable = false, length = 128, unique = true)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "consumed_at", nullable = false)
    private OffsetDateTime consumedAt;
}
