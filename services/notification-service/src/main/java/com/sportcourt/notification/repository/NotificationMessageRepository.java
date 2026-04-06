package com.sportcourt.notification.repository;

import com.sportcourt.notification.domain.NotificationChannel;
import com.sportcourt.notification.domain.NotificationMessage;
import com.sportcourt.notification.domain.enums.NotificationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationMessageRepository extends JpaRepository<NotificationMessage, UUID>, JpaSpecificationExecutor<NotificationMessage> {

    Optional<NotificationMessage> findBySourceEventIdAndChannelAndRecipientAndTemplateCode(
        String sourceEventId,
        NotificationChannel channel,
        String recipient,
        String templateCode
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationMessage n " +
        "where n.status in :statuses and n.nextAttemptAt <= :now " +
        "order by n.createdAt asc")
    List<NotificationMessage> findBatchForDispatch(
        @Param("statuses") List<NotificationStatus> statuses,
        @Param("now") OffsetDateTime now,
        Pageable pageable
    );

    long countByStatus(NotificationStatus status);

    Page<NotificationMessage> findByBookingIdOrderByCreatedAtDesc(UUID bookingId, Pageable pageable);

    Page<NotificationMessage> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
}
