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
import org.springframework.data.jpa.repository.Modifying;
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

    Page<NotificationMessage> findByCustomerIdAndChannelAndStatusOrderByCreatedAtDesc(
        UUID customerId,
        NotificationChannel channel,
        NotificationStatus status,
        Pageable pageable
    );

    Page<NotificationMessage> findByCustomerIdAndChannelAndStatusAndReadAtIsNullOrderByCreatedAtDesc(
        UUID customerId,
        NotificationChannel channel,
        NotificationStatus status,
        Pageable pageable
    );

    long countByCustomerIdAndChannelAndStatusAndReadAtIsNull(
        UUID customerId,
        NotificationChannel channel,
        NotificationStatus status
    );

    Optional<NotificationMessage> findByIdAndCustomerIdAndChannelAndStatus(
        UUID id,
        UUID customerId,
        NotificationChannel channel,
        NotificationStatus status
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update NotificationMessage n set n.readAt = :readAt, n.updatedAt = :readAt " +
        "where n.customerId = :customerId and n.channel = :channel and n.status = :status and n.readAt is null")
    int markAllRead(
        @Param("customerId") UUID customerId,
        @Param("channel") NotificationChannel channel,
        @Param("status") NotificationStatus status,
        @Param("readAt") OffsetDateTime readAt
    );
}
