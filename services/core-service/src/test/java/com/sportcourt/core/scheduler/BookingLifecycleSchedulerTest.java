package com.sportcourt.core.scheduler;

import com.sportcourt.core.domain.Booking;
import com.sportcourt.core.domain.enums.BookingStatus;
import com.sportcourt.core.domain.enums.PaymentStatus;
import com.sportcourt.core.event.BookingEventType;
import com.sportcourt.core.event.BookingOutboxService;
import com.sportcourt.core.repository.BookingRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingLifecycleSchedulerTest {

    @Test
    void moveConfirmedToInProgress_shouldAdvanceEligibleBookings() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingOutboxService bookingOutboxService = mock(BookingOutboxService.class);
        BookingLifecycleScheduler scheduler = new BookingLifecycleScheduler(bookingRepository, bookingOutboxService);
        Booking booking = booking(BookingStatus.CONFIRMED, PaymentStatus.DEPOSITED);

        when(bookingRepository.findByStatusAndStartTimeLessThanEqual(any(), any())).thenReturn(List.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.moveConfirmedToInProgress();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.IN_PROGRESS);
        verify(bookingRepository, times(1)).save(booking);
        verify(bookingOutboxService, times(1)).enqueue(BookingEventType.BOOKING_IN_PROGRESS, booking);
    }

    @Test
    void moveConfirmedToInProgress_shouldSkipUnpaidBookings() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingOutboxService bookingOutboxService = mock(BookingOutboxService.class);
        BookingLifecycleScheduler scheduler = new BookingLifecycleScheduler(bookingRepository, bookingOutboxService);
        Booking booking = booking(BookingStatus.CONFIRMED, PaymentStatus.UNPAID);

        when(bookingRepository.findByStatusAndStartTimeLessThanEqual(any(), any())).thenReturn(List.of(booking));

        scheduler.moveConfirmedToInProgress();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        verify(bookingRepository, times(0)).save(any());
        verify(bookingOutboxService, times(0)).enqueue(any(), any());
    }

    @Test
    void moveInProgressToCompleted_shouldAdvanceEndedBookings() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        BookingOutboxService bookingOutboxService = mock(BookingOutboxService.class);
        BookingLifecycleScheduler scheduler = new BookingLifecycleScheduler(bookingRepository, bookingOutboxService);
        Booking booking = booking(BookingStatus.IN_PROGRESS, PaymentStatus.DEPOSITED);

        when(bookingRepository.findByStatusAndEndTimeLessThanEqual(any(), any())).thenReturn(List.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        scheduler.moveInProgressToCompleted();

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.COMPLETED);
        verify(bookingRepository, times(1)).save(booking);
        verify(bookingOutboxService, times(1)).enqueue(BookingEventType.BOOKING_COMPLETED, booking);
    }

    private Booking booking(BookingStatus status, PaymentStatus paymentStatus) {
        Booking booking = new Booking();
        booking.setId(UUID.randomUUID());
        booking.setStatus(status);
        booking.setPaymentStatus(paymentStatus);
        booking.setStartTime(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10));
        booking.setEndTime(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        return booking;
    }
}
