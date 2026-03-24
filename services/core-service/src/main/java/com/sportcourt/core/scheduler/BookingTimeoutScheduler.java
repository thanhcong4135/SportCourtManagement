package com.sportcourt.core.scheduler;

import com.sportcourt.core.domain.Booking;
import com.sportcourt.core.domain.enums.BookingStatus;
import com.sportcourt.core.domain.enums.PaymentStatus;
import com.sportcourt.core.event.BookingOutboxService;
import com.sportcourt.core.event.BookingEventType;
import com.sportcourt.core.repository.BookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
public class BookingTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingTimeoutScheduler.class);

    private final BookingRepository bookingRepository;
    private final BookingOutboxService bookingOutboxService;
    private final int depositDeadlineMinutes;

    public BookingTimeoutScheduler(BookingRepository bookingRepository,
                                   BookingOutboxService bookingOutboxService,
                                   @Value("${booking.deposit.deadline-minutes:30}") int depositDeadlineMinutes) {
        this.bookingRepository = bookingRepository;
        this.bookingOutboxService = bookingOutboxService;
        this.depositDeadlineMinutes = depositDeadlineMinutes;
    }

    @Scheduled(fixedDelay = 60_000)
    public void cancelUnpaidDraftsBeforeStart() {
        OffsetDateTime cutoff = OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(depositDeadlineMinutes);
        List<Booking> drafts = bookingRepository.findByStatusAndStartTimeBefore(BookingStatus.DRAFT, cutoff);
        for (Booking b : drafts) {
            if (b.getPaymentStatus() == PaymentStatus.DEPOSITED || b.getPaymentStatus() == PaymentStatus.PAID) {
                continue;
            }
            b.setStatus(BookingStatus.FAILED_TIMEOUT);
            Booking saved = bookingRepository.save(b);
            bookingOutboxService.enqueue(BookingEventType.BOOKING_TIMEOUT, saved);
            log.info("Booking {} timed out due to missing deposit before start", b.getId());
        }
    }
}
