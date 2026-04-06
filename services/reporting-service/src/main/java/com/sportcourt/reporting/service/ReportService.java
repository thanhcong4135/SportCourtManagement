package com.sportcourt.reporting.service;

import com.sportcourt.reporting.dto.BestHourReportRowResponse;
import com.sportcourt.reporting.dto.OccupancyReportRowResponse;
import com.sportcourt.reporting.dto.ReportPageResponse;
import com.sportcourt.reporting.dto.RevenueReportRowResponse;
import com.sportcourt.reporting.repository.BookingReadModelRepository;
import com.sportcourt.reporting.repository.ProjectedEventRepository;
import com.sportcourt.reporting.repository.SalesOrderReadModelRepository;
import com.sportcourt.reporting.repository.projection.BestHourProjection;
import com.sportcourt.reporting.repository.projection.DailyAddOnRevenueProjection;
import com.sportcourt.reporting.repository.projection.DailyBookingAggregateProjection;
import com.sportcourt.reporting.repository.projection.DailyRevenueAggregateProjection;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ReportService {

    private final BookingReadModelRepository bookingReadModelRepository;
    private final SalesOrderReadModelRepository salesOrderReadModelRepository;
    private final ProjectedEventRepository projectedEventRepository;

    public ReportService(BookingReadModelRepository bookingReadModelRepository,
                         SalesOrderReadModelRepository salesOrderReadModelRepository,
                         ProjectedEventRepository projectedEventRepository) {
        this.bookingReadModelRepository = bookingReadModelRepository;
        this.salesOrderReadModelRepository = salesOrderReadModelRepository;
        this.projectedEventRepository = projectedEventRepository;
    }

    @Transactional(readOnly = true)
    public ReportPageResponse<OccupancyReportRowResponse> occupancy(LocalDate fromDate,
                                                                    LocalDate toDate,
                                                                    UUID venueId,
                                                                    Pageable pageable,
                                                                    String sort) {
        DateRange dateRange = validateDateRange(fromDate, toDate);
        List<DailyBookingAggregateProjection> rows = bookingReadModelRepository.aggregateOccupancy(
            dateRange.fromTime(),
            dateRange.toTime(),
            venueId
        );

        List<OccupancyReportRowResponse> mapped = rows.stream()
            .map(row -> new OccupancyReportRowResponse(
                row.getReportDate(),
                toUuid(row.getVenueId()),
                row.getTotalBookings() == null ? 0L : row.getTotalBookings(),
                scale(row.getBookedHours())
            ))
            .toList();

        return toPage(mapped, pageable, occupancyComparator(sort));
    }

    @Transactional(readOnly = true)
    public ReportPageResponse<RevenueReportRowResponse> revenue(LocalDate fromDate,
                                                                LocalDate toDate,
                                                                UUID venueId,
                                                                Pageable pageable,
                                                                String sort) {
        DateRange dateRange = validateDateRange(fromDate, toDate);
        List<DailyRevenueAggregateProjection> bookingRows = bookingReadModelRepository.aggregateRevenue(
            dateRange.fromTime(),
            dateRange.toTime(),
            venueId
        );
        List<DailyAddOnRevenueProjection> addOnRows = salesOrderReadModelRepository.aggregateAddOnRevenue(
            dateRange.fromTime(),
            dateRange.toTime(),
            venueId
        );

        Map<String, RevenueAggregate> aggregates = new HashMap<>();
        for (DailyRevenueAggregateProjection row : bookingRows) {
            UUID venueIdValue = toUuid(row.getVenueId());
            String key = key(row.getReportDate(), venueIdValue);
            RevenueAggregate aggregate = aggregates.computeIfAbsent(key, ignored -> new RevenueAggregate(row.getReportDate(), venueIdValue));
            aggregate.bookingRevenue = scale(row.getBookingRevenue());
            aggregate.depositRevenue = scale(row.getDepositRevenue());
        }
        for (DailyAddOnRevenueProjection row : addOnRows) {
            UUID venueIdValue = toUuid(row.getVenueId());
            String key = key(row.getReportDate(), venueIdValue);
            RevenueAggregate aggregate = aggregates.computeIfAbsent(key, ignored -> new RevenueAggregate(row.getReportDate(), venueIdValue));
            aggregate.addOnRevenue = scale(row.getAddOnRevenue());
        }

        List<RevenueReportRowResponse> rows = aggregates.values().stream()
            .map(aggregate -> {
                BigDecimal total = aggregate.bookingRevenue.add(aggregate.depositRevenue).add(aggregate.addOnRevenue);
                return new RevenueReportRowResponse(
                    aggregate.reportDate,
                    aggregate.venueId,
                    aggregate.bookingRevenue,
                    aggregate.depositRevenue,
                    aggregate.addOnRevenue,
                    scale(total)
                );
            })
            .toList();

        return toPage(rows, pageable, revenueComparator(sort));
    }

    @Transactional(readOnly = true)
    public List<BestHourReportRowResponse> bestHours(LocalDate fromDate,
                                                     LocalDate toDate,
                                                     UUID venueId,
                                                     Integer top) {
        DateRange dateRange = validateDateRange(fromDate, toDate);
        int topValue = (top == null || top <= 0) ? 5 : Math.min(top, 24);
        List<BestHourProjection> rows = bookingReadModelRepository.aggregateBestHours(
            dateRange.fromTime(),
            dateRange.toTime(),
            venueId
        );
        return rows.stream()
            .map(row -> new BestHourReportRowResponse(
                row.getHourOfDay(),
                row.getBookingCount() == null ? 0L : row.getBookingCount(),
                scale(row.getBookedHours())
            ))
            .sorted(
                Comparator.comparing(BestHourReportRowResponse::bookingCount).reversed()
                    .thenComparing(BestHourReportRowResponse::bookedHours).reversed()
                    .thenComparing(BestHourReportRowResponse::hourOfDay)
            )
            .limit(topValue)
            .toList();
    }

    @Transactional
    public void resetProjections() {
        projectedEventRepository.deleteAllInBatch();
        bookingReadModelRepository.deleteAllInBatch();
        salesOrderReadModelRepository.deleteAllInBatch();
    }

    private Comparator<OccupancyReportRowResponse> occupancyComparator(String sort) {
        if ("bookedHours,asc".equalsIgnoreCase(sort)) {
            return Comparator.comparing(OccupancyReportRowResponse::bookedHours)
                .thenComparing(OccupancyReportRowResponse::reportDate);
        }
        if ("bookedHours,desc".equalsIgnoreCase(sort)) {
            return Comparator.comparing(OccupancyReportRowResponse::bookedHours).reversed()
                .thenComparing(OccupancyReportRowResponse::reportDate).reversed();
        }
        return Comparator.comparing(OccupancyReportRowResponse::reportDate)
            .thenComparing(OccupancyReportRowResponse::venueId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Comparator<RevenueReportRowResponse> revenueComparator(String sort) {
        if ("totalRevenue,asc".equalsIgnoreCase(sort)) {
            return Comparator.comparing(RevenueReportRowResponse::totalRevenue)
                .thenComparing(RevenueReportRowResponse::reportDate);
        }
        if ("totalRevenue,desc".equalsIgnoreCase(sort)) {
            return Comparator.comparing(RevenueReportRowResponse::totalRevenue).reversed()
                .thenComparing(RevenueReportRowResponse::reportDate).reversed();
        }
        return Comparator.comparing(RevenueReportRowResponse::reportDate)
            .thenComparing(RevenueReportRowResponse::venueId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private <T> ReportPageResponse<T> toPage(List<T> rows, Pageable pageable, Comparator<T> comparator) {
        List<T> sorted = new ArrayList<>(rows);
        sorted.sort(comparator);
        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int from = Math.min(page * size, sorted.size());
        int to = Math.min(from + size, sorted.size());
        List<T> items = sorted.subList(from, to);
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) sorted.size() / size);
        return new ReportPageResponse<>(items, sorted.size(), page, size, totalPages);
    }

    private DateRange validateDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate start = fromDate == null ? LocalDate.now().minusDays(7) : fromDate;
        LocalDate end = toDate == null ? LocalDate.now().plusDays(1) : toDate;
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid date range");
        }
        return new DateRange(
            start.atStartOfDay().atOffset(ZoneOffset.UTC),
            end.atStartOfDay().atOffset(ZoneOffset.UTC)
        );
    }

    private String key(LocalDate reportDate, UUID venueId) {
        return reportDate + "|" + venueId;
    }

    private UUID toUuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID uuid) {
            return uuid;
        }
        if (value instanceof byte[] bytes && bytes.length == 16) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new UUID(buffer.getLong(), buffer.getLong());
        }
        if (value instanceof String text && !text.isBlank()) {
            return UUID.fromString(text);
        }
        throw new IllegalArgumentException("Unsupported venueId representation: " + value.getClass().getName());
    }

    private BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record DateRange(OffsetDateTime fromTime, OffsetDateTime toTime) {
    }

    private static final class RevenueAggregate {
        private final LocalDate reportDate;
        private final UUID venueId;
        private BigDecimal bookingRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal depositRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        private BigDecimal addOnRevenue = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        private RevenueAggregate(LocalDate reportDate, UUID venueId) {
            this.reportDate = reportDate;
            this.venueId = venueId;
        }
    }
}
