package com.sportcourt.core.dlq;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {

    boolean existsByDeadLetterTopicAndKafkaPartitionAndKafkaOffset(String deadLetterTopic,
                                                                   int kafkaPartition,
                                                                   long kafkaOffset);

    Page<DeadLetterEvent> findByStatusOrderByReceivedAtDesc(DeadLetterEventStatus status, Pageable pageable);

    Page<DeadLetterEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
