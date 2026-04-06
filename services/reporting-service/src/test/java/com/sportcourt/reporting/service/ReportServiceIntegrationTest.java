package com.sportcourt.reporting.service;

import com.sportcourt.reporting.dto.BestHourReportRowResponse;
import com.sportcourt.reporting.dto.OccupancyReportRowResponse;
import com.sportcourt.reporting.dto.ReportPageResponse;
import com.sportcourt.reporting.dto.RevenueReportRowResponse;
import com.sportcourt.reporting.repository.BookingReadModelRepository;
import com.sportcourt.reporting.repository.ProjectedEventRepository;
import com.sportcourt.reporting.repository.SalesOrderReadModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ReportServiceIntegrationTest {

    @Autowired
    private ProjectionEventService projectionEventService;

    @Autowired
    private BookingProjectionService bookingProjectionService;

    @Autowired
    private SalesProjectionService salesProjectionService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private ProjectedEventRepository projectedEventRepository;

    @Autowired
    private BookingReadModelRepository bookingReadModelRepository;

    @Autowired
    private SalesOrderReadModelRepository salesOrderReadModelRepository;

    @BeforeEach
    void setUp() {
        projectedEventRepository.deleteAll();
        bookingReadModelRepository.deleteAll();
        salesOrderReadModelRepository.deleteAll();
    }

    @Test
    void reserve_shouldBeIdempotent() {
        boolean first = projectionEventService.reserve("event-001", "booking.events");
        boolean second = projectionEventService.reserve("event-001", "booking.events");

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(projectedEventRepository.count()).isEqualTo(1);
    }

    @Test
    void reports_shouldAggregateFromProjectedReadModel() {
        UUID venueId = UUID.randomUUID();
        UUID booking1 = UUID.randomUUID();
        UUID booking2 = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 4, 6);

        bookingProjectionService.projectBookingSnapshot(
            booking1,
            venueId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "CONFIRMED",
            "DEPOSITED",
            time(day, 8, 0),
            time(day, 10, 0),
            money("300000"),
            "BOOKING_CONFIRMED",
            time(day, 7, 0)
        );
        bookingProjectionService.projectBookingSnapshot(
            booking2,
            venueId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "COMPLETED",
            "DEPOSITED",
            time(day, 10, 0),
            time(day, 12, 0),
            money("200000"),
            "BOOKING_COMPLETED",
            time(day, 12, 30)
        );
        bookingProjectionService.projectDeposit(booking1, money("150000"), time(day, 7, 10));
        bookingProjectionService.projectDeposit(booking2, money("100000"), time(day, 10, 10));
        salesProjectionService.projectOrderCreated(
            UUID.randomUUID(),
            booking1,
            venueId,
            UUID.randomUUID(),
            money("50000"),
            "CREATED",
            "SALES_ORDER_CREATED",
            time(day, 9, 0)
        );

        ReportPageResponse<OccupancyReportRowResponse> occupancy = reportService.occupancy(
            day,
            day.plusDays(1),
            venueId,
            PageRequest.of(0, 20),
            "reportDate,asc"
        );
        ReportPageResponse<RevenueReportRowResponse> revenue = reportService.revenue(
            day,
            day.plusDays(1),
            venueId,
            PageRequest.of(0, 20),
            "reportDate,asc"
        );
        List<BestHourReportRowResponse> bestHours = reportService.bestHours(day, day.plusDays(1), venueId, 3);

        assertThat(occupancy.items()).hasSize(1);
        assertThat(occupancy.items().get(0).totalBookings()).isEqualTo(2);
        assertThat(occupancy.items().get(0).bookedHours()).isEqualByComparingTo(money("4.00"));

        assertThat(revenue.items()).hasSize(1);
        RevenueReportRowResponse revenueRow = revenue.items().get(0);
        assertThat(revenueRow.bookingRevenue()).isEqualByComparingTo(money("500000.00"));
        assertThat(revenueRow.depositRevenue()).isEqualByComparingTo(money("250000.00"));
        assertThat(revenueRow.addOnRevenue()).isEqualByComparingTo(money("50000.00"));
        assertThat(revenueRow.totalRevenue()).isEqualByComparingTo(money("800000.00"));

        assertThat(bestHours).isNotEmpty();
        assertThat(bestHours.get(0).bookingCount()).isGreaterThan(0);
    }

    private static OffsetDateTime time(LocalDate date, int hour, int minute) {
        return OffsetDateTime.of(date.getYear(), date.getMonthValue(), date.getDayOfMonth(), hour, minute, 0, 0, ZoneOffset.UTC);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
