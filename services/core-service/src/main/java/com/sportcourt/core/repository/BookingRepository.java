package com.sportcourt.core.repository;

import com.sportcourt.core.domain.Booking;
import com.sportcourt.core.domain.enums.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID>, JpaSpecificationExecutor<Booking> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id in :ids")
    List<Booking> findAllByIdInForUpdate(@Param("ids") List<UUID> ids);

    @Query("select count(b) > 0 from Booking b " +
           "where b.court.id = :courtId " +
           "and b.status in :statuses " +
           "and b.startTime < :endTime " +
           "and b.endTime > :startTime")
    boolean existsOverlap(@Param("courtId") UUID courtId,
                          @Param("startTime") OffsetDateTime startTime,
                          @Param("endTime") OffsetDateTime endTime,
                          @Param("statuses") List<BookingStatus> statuses);

    @Query("select count(b) > 0 from Booking b " +
           "where b.court.id = :courtId " +
           "and b.status in :statuses " +
           "and b.id <> :excludeId " +
           "and b.startTime < :endTime " +
           "and b.endTime > :startTime")
    boolean existsOverlapExcluding(@Param("courtId") UUID courtId,
                                   @Param("startTime") OffsetDateTime startTime,
                                   @Param("endTime") OffsetDateTime endTime,
                                   @Param("statuses") List<BookingStatus> statuses,
                                   @Param("excludeId") UUID excludeId);

    @Query("select b from Booking b " +
           "where b.court.id in :courtIds " +
           "and b.status in :statuses " +
           "and b.startTime < :endTime " +
           "and b.endTime > :startTime " +
           "order by b.court.id, b.startTime")
    List<Booking> findAvailabilityBlocks(@Param("courtIds") List<UUID> courtIds,
                                         @Param("startTime") OffsetDateTime startTime,
                                         @Param("endTime") OffsetDateTime endTime,
                                         @Param("statuses") List<BookingStatus> statuses);

    List<Booking> findByStatusAndStartTimeBefore(BookingStatus status, OffsetDateTime time);

    List<Booking> findByStatusAndStartTimeLessThanEqual(BookingStatus status, OffsetDateTime time);

    List<Booking> findByStatusAndEndTimeLessThanEqual(BookingStatus status, OffsetDateTime time);

    Optional<Booking> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);
}
