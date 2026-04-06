package com.sportcourt.reporting.service;

import com.sportcourt.reporting.domain.BookingReadModel;
import com.sportcourt.reporting.repository.BookingReadModelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class BookingProjectionService {

    private final BookingReadModelRepository bookingReadModelRepository;

    public BookingProjectionService(BookingReadModelRepository bookingReadModelRepository) {
        this.bookingReadModelRepository = bookingReadModelRepository;
    }

    @Transactional
    public void projectBookingSnapshot(UUID bookingId,
                                       UUID venueId,
                                       UUID courtId,
                                       UUID customerId,
                                       String status,
                                       String paymentStatus,
                                       OffsetDateTime startTime,
                                       OffsetDateTime endTime,
                                       BigDecimal priceTotal,
                                       String eventType,
                                       OffsetDateTime occurredAt) {
        BookingReadModel model = bookingReadModelRepository.findById(bookingId).orElseGet(() -> {
            BookingReadModel fresh = new BookingReadModel();
            fresh.setBookingId(bookingId);
            fresh.setDepositPaid(BigDecimal.ZERO);
            return fresh;
        });

        model.setVenueId(venueId != null ? venueId : model.getVenueId());
        model.setCourtId(courtId != null ? courtId : model.getCourtId());
        model.setCustomerId(customerId != null ? customerId : model.getCustomerId());
        model.setStatus(status != null ? status : model.getStatus());
        model.setPaymentStatus(paymentStatus != null ? paymentStatus : model.getPaymentStatus());
        model.setStartTime(startTime != null ? startTime.withOffsetSameInstant(ZoneOffset.UTC) : model.getStartTime());
        model.setEndTime(endTime != null ? endTime.withOffsetSameInstant(ZoneOffset.UTC) : model.getEndTime());
        model.setPriceTotal(priceTotal != null ? priceTotal : (model.getPriceTotal() != null ? model.getPriceTotal() : BigDecimal.ZERO));
        model.setLastEventType(eventType);
        model.setLastOccurredAt(occurredAt != null ? occurredAt.withOffsetSameInstant(ZoneOffset.UTC) : OffsetDateTime.now(ZoneOffset.UTC));
        model.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bookingReadModelRepository.save(model);
    }

    @Transactional
    public void projectDeposit(UUID bookingId, BigDecimal depositPaid, OffsetDateTime occurredAt) {
        BookingReadModel model = bookingReadModelRepository.findById(bookingId).orElseGet(() -> {
            BookingReadModel fresh = new BookingReadModel();
            fresh.setBookingId(bookingId);
            fresh.setStatus("DRAFT");
            fresh.setPriceTotal(BigDecimal.ZERO);
            fresh.setDepositPaid(BigDecimal.ZERO);
            fresh.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
            return fresh;
        });

        if (depositPaid != null && depositPaid.compareTo(model.getDepositPaid()) > 0) {
            model.setDepositPaid(depositPaid);
        }
        model.setLastOccurredAt(occurredAt != null ? occurredAt.withOffsetSameInstant(ZoneOffset.UTC) : OffsetDateTime.now(ZoneOffset.UTC));
        model.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        bookingReadModelRepository.save(model);
    }
}
