package com.sportcourt.core.scheduler;

import com.sportcourt.core.domain.Booking;
import com.sportcourt.core.domain.enums.BookingStatus;
import com.sportcourt.core.domain.enums.PaymentStatus;
import com.sportcourt.core.event.BookingEventType;
import com.sportcourt.core.event.BookingOutboxService;
import com.sportcourt.core.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class BookingLifecycleScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingLifecycleScheduler.class);

    private final BookingRepository bookingRepository;
    private final BookingOutboxService bookingOutboxService;

    public BookingLifecycleScheduler(BookingRepository bookingRepository,
                                     BookingOutboxService bookingOutboxService) {
        this.bookingRepository = bookingRepository;
        this.bookingOutboxService = bookingOutboxService;
    }

    @Scheduled(fixedDelayString = "${booking.lifecycle.scheduler.fixed-delay-ms:60000}")
    @Transactional
    public void moveConfirmedToInProgress() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Booking> bookings = bookingRepository.findByStatusAndStartTimeLessThanEqual(BookingStatus.CONFIRMED, now);
        for (Booking booking : bookings) {
            if (!canStart(booking.getPaymentStatus())) {
                continue;
            }
            booking.setStatus(BookingStatus.IN_PROGRESS);
            Booking saved = bookingRepository.save(booking);
            bookingOutboxService.enqueue(BookingEventType.BOOKING_IN_PROGRESS, saved);
            log.info("Booking {} moved to IN_PROGRESS", saved.getId());
        }
    }

    @Scheduled(fixedDelayString = "${booking.lifecycle.scheduler.fixed-delay-ms:60000}")
    @Transactional
    public void moveInProgressToCompleted() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        List<Booking> bookings = bookingRepository.findByStatusAndEndTimeLessThanEqual(BookingStatus.IN_PROGRESS, now);
        for (Booking booking : bookings) {
            booking.setStatus(BookingStatus.COMPLETED);
            Booking saved = bookingRepository.save(booking);
            bookingOutboxService.enqueue(BookingEventType.BOOKING_COMPLETED, saved);
            log.info("Booking {} moved to COMPLETED", saved.getId());
        }
    }

    private boolean canStart(PaymentStatus paymentStatus) {
        return paymentStatus == PaymentStatus.DEPOSITED || paymentStatus == PaymentStatus.PAID;
    }
}
