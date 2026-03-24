package com.sportcourt.core.repository;

import com.sportcourt.core.domain.OutboxEvent;
import com.sportcourt.core.domain.enums.OutboxEventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e " +
           "where e.status = :status and (e.nextAttemptAt is null or e.nextAttemptAt <= :now) " +
           "order by e.createdAt asc")
    List<OutboxEvent> findBatchForPublish(@Param("status") OutboxEventStatus status,
                                          @Param("now") OffsetDateTime now,
                                          Pageable pageable);

    long countByStatus(OutboxEventStatus status);

    Optional<OutboxEvent> findFirstByStatusOrderByCreatedAtAsc(OutboxEventStatus status);
}
