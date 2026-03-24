package com.sportcourt.core.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.core.api.PageResponse;
import com.sportcourt.core.domain.Court;
import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.domain.enums.BookingStatus;
import com.sportcourt.core.domain.enums.PaymentStatus;
import com.sportcourt.core.domain.enums.SportType;
import com.sportcourt.core.dto.BatchBookingActionResponse;
import com.sportcourt.core.dto.BatchBookingDraftRequest;
import com.sportcourt.core.dto.BatchBookingDraftResponse;
import com.sportcourt.core.dto.BatchConfirmRequest;
import com.sportcourt.core.dto.BatchDepositItemRequest;
import com.sportcourt.core.dto.BatchDepositRequest;
import com.sportcourt.core.dto.BookingDraftItemRequest;
import com.sportcourt.core.dto.BookingDraftRequest;
import com.sportcourt.core.dto.BookingResponse;
import com.sportcourt.core.dto.CourtCreateRequest;
import com.sportcourt.core.dto.DepositPaymentRequest;
import com.sportcourt.core.dto.VenueCreateRequest;
import com.sportcourt.core.domain.OutboxEvent;
import com.sportcourt.core.domain.enums.OutboxEventStatus;
import com.sportcourt.core.event.BookingEventType;
import com.sportcourt.core.event.BookingEventPublisher;
import com.sportcourt.core.event.PaymentEvent;
import com.sportcourt.core.event.PaymentEventType;
import com.sportcourt.core.repository.BookingRepository;
import com.sportcourt.core.repository.CourtRepository;
import com.sportcourt.core.repository.OutboxEventRepository;
import com.sportcourt.core.repository.VenueRepository;
import com.sportcourt.core.scheduler.OutboxPublisherScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class BookingServiceIntegrationTest {

    private static final ZoneOffset PLUS_7 = ZoneOffset.ofHours(7);

    @Autowired
    private BookingService bookingService;

    @Autowired
    private VenueService venueService;

    @Autowired
    private CourtService courtService;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private OutboxPublisherScheduler outboxPublisherScheduler;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingEventPublisher bookingEventPublisher;

    @BeforeEach
    void setUp() {
        outboxEventRepository.deleteAll();
        bookingRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void createDraftDepositConfirm_shouldMakeSlotUnavailable() {
        Court court = createCourt("Court-1");
        OffsetDateTime start = time(2026, 3, 10, 8, 0);
        OffsetDateTime end = time(2026, 3, 10, 10, 0);
        UUID customerId = UUID.randomUUID();

        assertThat(bookingService.isAvailable(court.getId(), start, end)).isTrue();

        BookingResponse draft = bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            customerId,
            start,
            end,
            money("400000")
        ));

        assertThat(draft.status()).isEqualTo(BookingStatus.DRAFT);
        assertThat(draft.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(draft.depositRequired()).isEqualByComparingTo(money("200000.00"));
        assertThat(draft.startTime()).isEqualTo(start);
        assertThat(draft.endTime()).isEqualTo(end);

        BookingResponse deposited = bookingService.payDeposit(
            draft.id(),
            new DepositPaymentRequest(money("200000"))
        );
        assertThat(deposited.paymentStatus()).isEqualTo(PaymentStatus.DEPOSITED);

        BookingResponse confirmed = bookingService.confirm(draft.id());
        assertThat(confirmed.status()).isEqualTo(BookingStatus.CONFIRMED);

        assertThat(bookingService.isAvailable(court.getId(), start, end)).isFalse();
        assertThat(bookingService.isAvailable(
            court.getId(),
            time(2026, 3, 10, 10, 0),
            time(2026, 3, 10, 12, 0)
        )).isTrue();
    }

    @Test
    void payDeposit_shouldRejectAmountBelowRequired() {
        Court court = createCourt("Court-1");
        BookingResponse draft = bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            UUID.randomUUID(),
            time(2026, 3, 11, 8, 0),
            time(2026, 3, 11, 10, 0),
            money("300000")
        ));

        assertThatThrownBy(() -> bookingService.payDeposit(draft.id(), new DepositPaymentRequest(money("100000"))))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void confirm_shouldRejectWhenBookingIsNotDeposited() {
        Court court = createCourt("Court-1");
        BookingResponse draft = bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            UUID.randomUUID(),
            time(2026, 3, 12, 8, 0),
            time(2026, 3, 12, 10, 0),
            money("300000")
        ));

        assertThatThrownBy(() -> bookingService.confirm(draft.id()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDraft_shouldRejectOverlapOnSameCourt() {
        Court court = createCourt("Court-1");
        OffsetDateTime start = time(2026, 3, 13, 8, 0);
        OffsetDateTime end = time(2026, 3, 13, 10, 0);

        bookingService.createDraft(new BookingDraftRequest(court.getId(), UUID.randomUUID(), start, end, money("200000")));

        assertThatThrownBy(() -> bookingService.createDraft(
            new BookingDraftRequest(court.getId(), UUID.randomUUID(), start.plusHours(1), end.plusHours(1), money("200000"))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void getById_shouldReturnExistingBooking() {
        Court court = createCourt("Court-1");
        BookingResponse draft = bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            UUID.randomUUID(),
            time(2026, 3, 16, 8, 0),
            time(2026, 3, 16, 10, 0),
            money("220000")
        ));

        BookingResponse found = bookingService.getById(draft.id());

        assertThat(found.id()).isEqualTo(draft.id());
        assertThat(found.courtId()).isEqualTo(court.getId());
        assertThat(found.status()).isEqualTo(BookingStatus.DRAFT);
    }

    @Test
    void list_shouldFilterByCustomerStatusAndDateRange() {
        Court court = createCourt("Court-1");
        UUID customerId = UUID.randomUUID();

        BookingResponse bookingA = bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            customerId,
            time(2026, 3, 17, 8, 0),
            time(2026, 3, 17, 10, 0),
            money("300000")
        ));
        BookingResponse bookingB = bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            customerId,
            time(2026, 3, 17, 19, 0),
            time(2026, 3, 17, 21, 0),
            money("350000")
        ));

        bookingService.payDeposit(bookingA.id(), new DepositPaymentRequest(money("150000")));
        bookingService.confirm(bookingA.id());

        PageResponse<BookingResponse> confirmedOnly = bookingService.list(
            customerId,
            court.getId(),
            BookingStatus.CONFIRMED,
            null,
            null,
            PageRequest.of(0, 20)
        );
        assertThat(confirmedOnly.items()).hasSize(1);
        assertThat(confirmedOnly.items().get(0).id()).isEqualTo(bookingA.id());

        PageResponse<BookingResponse> overlapByRange = bookingService.list(
            customerId,
            court.getId(),
            null,
            time(2026, 3, 17, 18, 0),
            time(2026, 3, 17, 20, 0),
            PageRequest.of(0, 20)
        );
        assertThat(overlapByRange.items()).hasSize(1);
        assertThat(overlapByRange.items().get(0).id()).isEqualTo(bookingB.id());
    }

    @Test
    void batchFlow_shouldCreateDepositAndConfirmAllBookings() {
        Court court1 = createCourt("Court-1");
        Court court2 = createCourt("Court-2");
        UUID customerId = UUID.randomUUID();

        BatchBookingDraftResponse batchDraft = bookingService.createBatchDraft(new BatchBookingDraftRequest(
            customerId,
            List.of(
                new BookingDraftItemRequest(court1.getId(), time(2026, 3, 14, 8, 0), time(2026, 3, 14, 10, 0), money("400000")),
                new BookingDraftItemRequest(court2.getId(), time(2026, 3, 14, 19, 0), time(2026, 3, 14, 21, 0), money("500000"))
            )
        ));

        assertThat(batchDraft.bookings()).hasSize(2);
        assertThat(batchDraft.totalPrice()).isEqualByComparingTo(money("900000"));
        assertThat(batchDraft.totalDepositRequired()).isEqualByComparingTo(money("450000.00"));

        BookingResponse first = batchDraft.bookings().get(0);
        BookingResponse second = batchDraft.bookings().get(1);

        BatchBookingActionResponse deposited = bookingService.payDepositBatch(new BatchDepositRequest(
            List.of(
                new BatchDepositItemRequest(first.id(), money("200000")),
                new BatchDepositItemRequest(second.id(), money("250000"))
            )
        ));
        assertThat(deposited.bookings()).extracting(BookingResponse::paymentStatus)
            .containsOnly(PaymentStatus.DEPOSITED);

        BatchBookingActionResponse confirmed = bookingService.confirmBatch(new BatchConfirmRequest(
            List.of(first.id(), second.id())
        ));

        assertThat(confirmed.bookings()).extracting(BookingResponse::status)
            .containsOnly(BookingStatus.CONFIRMED);
        assertThat(confirmed.totalPrice()).isEqualByComparingTo(money("900000"));
        assertThat(confirmed.totalDepositRequired()).isEqualByComparingTo(money("450000.00"));
        assertThat(confirmed.totalDepositPaid()).isEqualByComparingTo(money("450000"));
    }

    @Test
    void payDepositBatch_shouldRejectDuplicateBookingIds() {
        Court court = createCourt("Court-1");
        BookingResponse draft = bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            UUID.randomUUID(),
            time(2026, 3, 15, 8, 0),
            time(2026, 3, 15, 10, 0),
            money("200000")
        ));

        BatchDepositRequest req = new BatchDepositRequest(List.of(
            new BatchDepositItemRequest(draft.id(), money("100000")),
            new BatchDepositItemRequest(draft.id(), money("100000"))
        ));

        assertThatThrownBy(() -> bookingService.payDepositBatch(req))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createDraft_shouldEnqueueOutboxEvent() throws Exception {
        Court court = createCourt("Court-Outbox");
        bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            UUID.randomUUID(),
            time(2026, 3, 20, 8, 0),
            time(2026, 3, 20, 10, 0),
            money("200000")
        ));

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent event = events.get(0);
        assertThat(event.getAggregateType()).isEqualTo("BOOKING");
        assertThat(event.getEventType()).isEqualTo("BOOKING_DRAFT_CREATED");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getPayload()).isNotBlank();
        JsonNode payload = objectMapper.readTree(event.getPayload());
        assertThat(payload.path("eventId").asText()).isNotBlank();
    }

    @Test
    void applyPaymentEvent_success_shouldSetDepositedAndConfirmDraft() {
        Court court = createCourt("Court-Payment-Success");
        BookingResponse draft = bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            UUID.randomUUID(),
            time(2026, 3, 22, 8, 0),
            time(2026, 3, 22, 10, 0),
            money("300000")
        ));

        PaymentEvent event = new PaymentEvent();
        event.setEventId(UUID.randomUUID());
        event.setType(PaymentEventType.DEPOSIT_SUCCEEDED);
        event.setBookingId(draft.id());
        event.setCustomerId(draft.customerId());
        event.setAmount(money("150000"));

        BookingResponse updated = bookingService.applyPaymentEvent(event);

        assertThat(updated.paymentStatus()).isEqualTo(PaymentStatus.DEPOSITED);
        assertThat(updated.depositPaid()).isEqualByComparingTo(money("150000.00"));
        assertThat(updated.status()).isEqualTo(BookingStatus.CONFIRMED);

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).extracting(OutboxEvent::getEventType).contains(
            BookingEventType.BOOKING_DRAFT_CREATED.name(),
            BookingEventType.BOOKING_DEPOSITED.name(),
            BookingEventType.BOOKING_CONFIRMED.name()
        );
    }

    @Test
    void applyPaymentEvent_failed_shouldMarkPaymentFailed() {
        Court court = createCourt("Court-Payment-Failed");
        BookingResponse draft = bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            UUID.randomUUID(),
            time(2026, 3, 23, 8, 0),
            time(2026, 3, 23, 10, 0),
            money("300000")
        ));

        PaymentEvent event = new PaymentEvent();
        event.setEventId(UUID.randomUUID());
        event.setType(PaymentEventType.DEPOSIT_FAILED);
        event.setBookingId(draft.id());
        event.setCustomerId(draft.customerId());
        event.setAmount(money("150000"));

        BookingResponse updated = bookingService.applyPaymentEvent(event);

        assertThat(updated.paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updated.status()).isEqualTo(BookingStatus.DRAFT);

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).extracting(OutboxEvent::getEventType).contains(
            BookingEventType.BOOKING_DRAFT_CREATED.name(),
            BookingEventType.BOOKING_PAYMENT_FAILED.name()
        );
    }

    @Test
    void outboxScheduler_shouldPublishAndMarkEventSent() {
        Court court = createCourt("Court-Outbox-Scheduler");
        bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            UUID.randomUUID(),
            time(2026, 3, 21, 8, 0),
            time(2026, 3, 21, 10, 0),
            money("220000")
        ));

        outboxPublisherScheduler.publishPendingEvents();

        List<OutboxEvent> events = outboxEventRepository.findAll();
        assertThat(events).hasSize(1);
        OutboxEvent event = events.get(0);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(event.getSentAt()).isNotNull();
        verify(bookingEventPublisher).publishRaw(anyString(), anyString(), anyString(), anyString());
    }

    private Court createCourt(String name) {
        Venue venue = venueService.create(new VenueCreateRequest("Venue-" + name, "Address"));
        return courtService.create(new CourtCreateRequest(venue.getId(), name, SportType.BADMINTON));
    }

    private static OffsetDateTime time(int year, int month, int day, int hour, int minute) {
        return OffsetDateTime.of(year, month, day, hour, minute, 0, 0, PLUS_7);
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
