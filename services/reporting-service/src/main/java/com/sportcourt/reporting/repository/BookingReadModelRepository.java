package com.sportcourt.reporting.repository;

import com.sportcourt.reporting.domain.BookingReadModel;
import com.sportcourt.reporting.repository.projection.BestHourProjection;
import com.sportcourt.reporting.repository.projection.DailyBookingAggregateProjection;
import com.sportcourt.reporting.repository.projection.DailyRevenueAggregateProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface BookingReadModelRepository extends JpaRepository<BookingReadModel, UUID> {

    @Query(value = """
        SELECT
            DATE(b.start_time) AS reportDate,
            b.venue_id AS venueId,
            COUNT(*) AS totalBookings,
            CAST(COALESCE(SUM(TIMESTAMPDIFF(MINUTE, b.start_time, b.end_time)), 0) / 60.0 AS DECIMAL(12,2)) AS bookedHours
        FROM booking_read_model b
        WHERE b.start_time >= :fromTime
          AND b.start_time < :toTime
          AND b.status IN ('CONFIRMED','IN_PROGRESS','COMPLETED')
          AND (:venueId IS NULL OR b.venue_id = :venueId)
        GROUP BY DATE(b.start_time), b.venue_id
        """, nativeQuery = true)
    List<DailyBookingAggregateProjection> aggregateOccupancy(@Param("fromTime") OffsetDateTime fromTime,
                                                             @Param("toTime") OffsetDateTime toTime,
                                                             @Param("venueId") UUID venueId);

    @Query(value = """
        SELECT
            DATE(b.start_time) AS reportDate,
            b.venue_id AS venueId,
            CAST(COALESCE(SUM(CASE WHEN b.status IN ('CONFIRMED','IN_PROGRESS','COMPLETED') THEN b.price_total ELSE 0 END),0) AS DECIMAL(12,2)) AS bookingRevenue,
            CAST(COALESCE(SUM(CASE WHEN b.status IN ('CONFIRMED','IN_PROGRESS','COMPLETED') THEN b.deposit_paid ELSE 0 END),0) AS DECIMAL(12,2)) AS depositRevenue
        FROM booking_read_model b
        WHERE b.start_time >= :fromTime
          AND b.start_time < :toTime
          AND (:venueId IS NULL OR b.venue_id = :venueId)
        GROUP BY DATE(b.start_time), b.venue_id
        """, nativeQuery = true)
    List<DailyRevenueAggregateProjection> aggregateRevenue(@Param("fromTime") OffsetDateTime fromTime,
                                                           @Param("toTime") OffsetDateTime toTime,
                                                           @Param("venueId") UUID venueId);

    @Query(value = """
        SELECT
            HOUR(b.start_time) AS hourOfDay,
            COUNT(*) AS bookingCount,
            CAST(COALESCE(SUM(TIMESTAMPDIFF(MINUTE, b.start_time, b.end_time)),0) / 60.0 AS DECIMAL(12,2)) AS bookedHours
        FROM booking_read_model b
        WHERE b.start_time >= :fromTime
          AND b.start_time < :toTime
          AND b.status IN ('CONFIRMED','IN_PROGRESS','COMPLETED')
          AND (:venueId IS NULL OR b.venue_id = :venueId)
        GROUP BY HOUR(b.start_time)
        """, nativeQuery = true)
    List<BestHourProjection> aggregateBestHours(@Param("fromTime") OffsetDateTime fromTime,
                                                @Param("toTime") OffsetDateTime toTime,
                                                @Param("venueId") UUID venueId);
}
