package com.sportcourt.reporting.repository;

import com.sportcourt.reporting.domain.SalesOrderReadModel;
import com.sportcourt.reporting.repository.projection.DailyAddOnRevenueProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface SalesOrderReadModelRepository extends JpaRepository<SalesOrderReadModel, UUID> {

    @Query(value = """
        SELECT
            DATE(COALESCE(s.last_occurred_at, s.updated_at)) AS reportDate,
            s.venue_id AS venueId,
            CAST(COALESCE(SUM(CASE WHEN s.status = 'CREATED' THEN s.total_amount ELSE 0 END),0) AS DECIMAL(12,2)) AS addOnRevenue
        FROM sales_order_read_model s
        WHERE COALESCE(s.last_occurred_at, s.updated_at) >= :fromTime
          AND COALESCE(s.last_occurred_at, s.updated_at) < :toTime
          AND (:venueId IS NULL OR s.venue_id = :venueId)
        GROUP BY DATE(COALESCE(s.last_occurred_at, s.updated_at)), s.venue_id
        """, nativeQuery = true)
    List<DailyAddOnRevenueProjection> aggregateAddOnRevenue(@Param("fromTime") OffsetDateTime fromTime,
                                                            @Param("toTime") OffsetDateTime toTime,
                                                            @Param("venueId") UUID venueId);
}
