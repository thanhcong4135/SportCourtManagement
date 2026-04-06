package com.sportcourt.reporting.service;

import com.sportcourt.reporting.domain.SalesOrderReadModel;
import com.sportcourt.reporting.repository.SalesOrderReadModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class SalesProjectionService {

    private final SalesOrderReadModelRepository salesOrderReadModelRepository;

    public SalesProjectionService(SalesOrderReadModelRepository salesOrderReadModelRepository) {
        this.salesOrderReadModelRepository = salesOrderReadModelRepository;
    }

    @Transactional
    public void projectOrderCreated(UUID orderId,
                                    UUID bookingId,
                                    UUID venueId,
                                    UUID customerId,
                                    BigDecimal totalAmount,
                                    String status,
                                    String eventType,
                                    OffsetDateTime occurredAt) {
        SalesOrderReadModel model = salesOrderReadModelRepository.findById(orderId).orElseGet(() -> {
            SalesOrderReadModel fresh = new SalesOrderReadModel();
            fresh.setOrderId(orderId);
            return fresh;
        });
        model.setBookingId(bookingId);
        model.setVenueId(venueId);
        model.setCustomerId(customerId);
        model.setTotalAmount(totalAmount != null ? totalAmount : BigDecimal.ZERO);
        model.setStatus(status != null ? status : "CREATED");
        model.setLastEventType(eventType);
        model.setLastOccurredAt(occurredAt != null ? occurredAt.withOffsetSameInstant(ZoneOffset.UTC) : OffsetDateTime.now(ZoneOffset.UTC));
        model.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        salesOrderReadModelRepository.save(model);
    }
}
