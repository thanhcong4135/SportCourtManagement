package com.sportcourt.core.service;

import com.sportcourt.core.domain.Booking;
import com.sportcourt.core.domain.Court;
import com.sportcourt.core.domain.enums.BookingStatus;
import com.sportcourt.core.domain.enums.PaymentStatus;
import com.sportcourt.core.api.PageResponse;
import com.sportcourt.core.dto.BatchBookingActionResponse;
import com.sportcourt.core.dto.BatchBookingDraftRequest;
import com.sportcourt.core.dto.BatchBookingDraftResponse;
import com.sportcourt.core.dto.BatchConfirmRequest;
import com.sportcourt.core.dto.BatchDepositItemRequest;
import com.sportcourt.core.dto.BatchDepositRequest;
import com.sportcourt.core.dto.BookingDraftItemRequest;
import com.sportcourt.core.dto.BookingDraftRequest;
import com.sportcourt.core.dto.BookingResponse;
import com.sportcourt.core.dto.DepositPaymentRequest;
import com.sportcourt.core.event.PaymentEvent;
import com.sportcourt.core.event.PaymentEventType;
import com.sportcourt.core.event.BookingOutboxService;
import com.sportcourt.core.event.BookingEventType;
import com.sportcourt.core.repository.BookingRepository;
import com.sportcourt.core.repository.CourtRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BookingService {

    private static final int SLOT_MINUTES = 30;

    private final BookingRepository bookingRepository;
    private final CourtRepository courtRepository;
    private final BookingOutboxService bookingOutboxService;
    private final ZoneId responseZoneId;
    private final boolean autoConfirmOnDepositSuccess;

    public BookingService(BookingRepository bookingRepository,
                          CourtRepository courtRepository,
                          BookingOutboxService bookingOutboxService,
                          @Value("${app.time.response-zone:Asia/Ho_Chi_Minh}") String responseZone,
                          @Value("${booking.payment.auto-confirm-on-deposit-success:true}") boolean autoConfirmOnDepositSuccess) {
        this.bookingRepository = bookingRepository;
        this.courtRepository = courtRepository;
        this.bookingOutboxService = bookingOutboxService;
        this.responseZoneId = ZoneId.of(responseZone);
        this.autoConfirmOnDepositSuccess = autoConfirmOnDepositSuccess;
    }

    @Transactional(readOnly = true)
    public boolean isAvailable(UUID courtId, OffsetDateTime start, OffsetDateTime end) {
        validateTimeRange(start, end);
        validateSlotAlignment(start, end);
        OffsetDateTime normalizedStart = normalizeToUtc(start);
        OffsetDateTime normalizedEnd = normalizeToUtc(end);
        return !bookingRepository.existsOverlap(courtId, normalizedStart, normalizedEnd, activeStatuses());
    }

    @Transactional
    public BookingResponse createDraft(BookingDraftRequest req) {
        Court court = courtRepository.findByIdForUpdate(req.courtId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));

        Booking saved = createDraftInternal(court, req.customerId(), req.startTime(), req.endTime(), req.priceTotal());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public BookingResponse getById(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        return toResponse(booking);
    }

    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> list(UUID customerId,
                                              UUID courtId,
                                              BookingStatus status,
                                              OffsetDateTime from,
                                              OffsetDateTime to,
                                              Pageable pageable) {
        validateQueryRange(from, to);
        Specification<Booking> spec = buildQuerySpec(customerId, courtId, status, from, to);
        Page<BookingResponse> pageData = bookingRepository.findAll(spec, pageable).map(this::toResponse);
        return PageResponse.from(pageData);
    }

    @Transactional
    public BatchBookingDraftResponse createBatchDraft(BatchBookingDraftRequest req) {
        Map<UUID, Court> lockedCourts = lockCourts(req.items());
        BigDecimal totalPrice = BigDecimal.ZERO;
        BigDecimal totalDeposit = BigDecimal.ZERO;
        List<BookingResponse> responses = new ArrayList<>();

        for (BookingDraftItemRequest item : req.items()) {
            Court court = lockedCourts.get(item.courtId());
            Booking saved = createDraftInternal(court, req.customerId(), item.startTime(), item.endTime(), item.priceTotal());
            responses.add(toResponse(saved));
            totalPrice = totalPrice.add(saved.getPriceTotal());
            totalDeposit = totalDeposit.add(saved.getDepositRequired());
        }

        return new BatchBookingDraftResponse(responses, totalPrice, totalDeposit);
    }

    @Transactional
    public BookingResponse payDeposit(UUID bookingId, DepositPaymentRequest req) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        Booking saved = applyDeposit(booking, req.amount());
        return toResponse(saved);
    }

    @Transactional
    public BatchBookingActionResponse payDepositBatch(BatchDepositRequest req) {
        Map<UUID, BigDecimal> amountByBookingId = toAmountMap(req.items());
        Map<UUID, Booking> bookingsById = lockBookings(amountByBookingId.keySet().stream().toList());
        List<Booking> updated = new ArrayList<>();

        for (BatchDepositItemRequest item : req.items()) {
            Booking booking = bookingsById.get(item.bookingId());
            Booking saved = applyDeposit(booking, amountByBookingId.get(item.bookingId()));
            updated.add(saved);
        }

        return toBatchActionResponse(updated);
    }

    @Transactional
    public BookingResponse confirm(UUID bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
        Booking saved = confirmInternal(booking);
        return toResponse(saved);
    }

    @Transactional
    public BatchBookingActionResponse confirmBatch(BatchConfirmRequest req) {
        Map<UUID, Booking> bookingsById = lockBookings(req.bookingIds());
        List<Booking> orderedBookings = req.bookingIds().stream().map(bookingsById::get).toList();

        // Lock involved courts in deterministic order to minimize deadlock risk.
        List<UUID> courtIds = orderedBookings.stream()
            .map(b -> b.getCourt().getId())
            .distinct()
            .sorted(Comparator.comparing(UUID::toString))
            .toList();
        for (UUID courtId : courtIds) {
            courtRepository.findByIdForUpdate(courtId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));
        }

        List<Booking> updated = new ArrayList<>();
        for (Booking booking : orderedBookings) {
            Booking saved = confirmInternal(booking);
            updated.add(saved);
        }

        return toBatchActionResponse(updated);
    }

    @Transactional
    public BookingResponse cancel(UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (booking.getStatus() == BookingStatus.CANCELED) {
            return toResponse(booking);
        }

        booking.setStatus(BookingStatus.CANCELED);
        Booking saved = bookingRepository.save(booking);
        bookingOutboxService.enqueue(BookingEventType.BOOKING_CANCELED, saved);
        return toResponse(saved);
    }

    @Transactional
    public BookingResponse applyPaymentEvent(PaymentEvent paymentEvent) {
        if (paymentEvent == null || paymentEvent.getBookingId() == null || paymentEvent.getType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid payment event");
        }

        Booking booking = bookingRepository.findByIdForUpdate(paymentEvent.getBookingId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        if (isTerminalStatus(booking.getStatus())) {
            return toResponse(booking);
        }

        return switch (paymentEvent.getType()) {
            case DEPOSIT_SUCCEEDED -> toResponse(applyDepositSuccessFromPayment(booking, paymentEvent));
            case DEPOSIT_FAILED -> toResponse(applyDepositFailedFromPayment(booking));
        };
    }

    private Map<UUID, BigDecimal> toAmountMap(List<BatchDepositItemRequest> items) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        for (BatchDepositItemRequest item : items) {
            if (result.containsKey(item.bookingId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duplicate bookingId in batch deposit request");
            }
            result.put(item.bookingId(), item.amount());
        }
        return result;
    }

    private Map<UUID, Booking> lockBookings(List<UUID> bookingIds) {
        List<UUID> uniqueSorted = bookingIds.stream()
            .distinct()
            .sorted(Comparator.comparing(UUID::toString))
            .toList();

        List<Booking> bookings = bookingRepository.findAllByIdInForUpdate(uniqueSorted);
        if (bookings.size() != uniqueSorted.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "One or more bookings not found");
        }

        Map<UUID, Booking> result = new HashMap<>();
        for (Booking booking : bookings) {
            result.put(booking.getId(), booking);
        }
        return result;
    }

    private Map<UUID, Court> lockCourts(List<BookingDraftItemRequest> items) {
        Map<UUID, Court> result = new HashMap<>();
        items.stream()
            .map(BookingDraftItemRequest::courtId)
            .distinct()
            .sorted(Comparator.comparing(UUID::toString))
            .forEach(courtId -> {
                Court court = courtRepository.findByIdForUpdate(courtId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));
                result.put(courtId, court);
            });
        return result;
    }

    private Booking createDraftInternal(Court court,
                                        UUID customerId,
                                        OffsetDateTime startTime,
                                        OffsetDateTime endTime,
                                        BigDecimal priceTotal) {
        OffsetDateTime normalizedStart = normalizeToUtc(startTime);
        OffsetDateTime normalizedEnd = normalizeToUtc(endTime);

        if (!court.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Court is inactive");
        }

        if (!isAvailable(court.getId(), startTime, endTime)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Time slot not available");
        }

        Booking booking = new Booking();
        booking.setCourt(court);
        booking.setCustomerId(customerId);
        booking.setStatus(BookingStatus.DRAFT);
        booking.setPaymentStatus(PaymentStatus.UNPAID);
        booking.setStartTime(normalizedStart);
        booking.setEndTime(normalizedEnd);
        booking.setPriceTotal(priceTotal);
        booking.setDepositRequired(calcDepositRequired(priceTotal));
        booking.setDepositPaid(BigDecimal.ZERO);
        booking.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        Booking saved = bookingRepository.save(booking);
        bookingOutboxService.enqueue(BookingEventType.BOOKING_DRAFT_CREATED, saved);
        return saved;
    }

    private Specification<Booking> buildQuerySpec(UUID customerId,
                                                  UUID courtId,
                                                  BookingStatus status,
                                                  OffsetDateTime from,
                                                  OffsetDateTime to) {
        Specification<Booking> spec = Specification.where(null);

        if (customerId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("customerId"), customerId));
        }
        if (courtId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("court").get("id"), courtId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (from != null) {
            OffsetDateTime normalizedFrom = normalizeToUtc(from);
            spec = spec.and((root, query, cb) -> cb.greaterThan(root.get("endTime"), normalizedFrom));
        }
        if (to != null) {
            OffsetDateTime normalizedTo = normalizeToUtc(to);
            spec = spec.and((root, query, cb) -> cb.lessThan(root.get("startTime"), normalizedTo));
        }
        return spec;
    }

    private Booking applyDeposit(Booking booking, BigDecimal amount) {
        if (booking.getStatus() != BookingStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only DRAFT can accept deposit");
        }

        BigDecimal newPaid = booking.getDepositPaid().add(amount);
        if (newPaid.compareTo(booking.getDepositRequired()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit must be at least 50% of total");
        }

        booking.setDepositPaid(newPaid);
        booking.setPaymentStatus(PaymentStatus.DEPOSITED);
        Booking saved = bookingRepository.save(booking);
        bookingOutboxService.enqueue(BookingEventType.BOOKING_DEPOSITED, saved);
        return saved;
    }

    private Booking applyDepositSuccessFromPayment(Booking booking, PaymentEvent paymentEvent) {
        boolean depositStatusChanged = booking.getPaymentStatus() != PaymentStatus.DEPOSITED
            && booking.getPaymentStatus() != PaymentStatus.PAID;
        if (booking.getPaymentStatus() != PaymentStatus.PAID) {
            booking.setPaymentStatus(PaymentStatus.DEPOSITED);
        }

        BigDecimal amountFromEvent = paymentEvent.getAmount();
        BigDecimal newPaid = booking.getDepositPaid();
        if (amountFromEvent != null && amountFromEvent.compareTo(newPaid) > 0) {
            newPaid = amountFromEvent;
        }
        if (newPaid.compareTo(booking.getDepositRequired()) < 0) {
            newPaid = booking.getDepositRequired();
        }
        booking.setDepositPaid(newPaid);

        Booking saved = bookingRepository.save(booking);
        if (depositStatusChanged) {
            bookingOutboxService.enqueue(BookingEventType.BOOKING_DEPOSITED, saved);
        }

        if (autoConfirmOnDepositSuccess && saved.getStatus() == BookingStatus.DRAFT) {
            saved = confirmInternal(saved);
        }
        return saved;
    }

    private Booking applyDepositFailedFromPayment(Booking booking) {
        if (booking.getPaymentStatus() == PaymentStatus.DEPOSITED || booking.getPaymentStatus() == PaymentStatus.PAID) {
            return booking;
        }
        if (booking.getPaymentStatus() == PaymentStatus.FAILED) {
            return booking;
        }

        booking.setPaymentStatus(PaymentStatus.FAILED);
        Booking saved = bookingRepository.save(booking);
        bookingOutboxService.enqueue(BookingEventType.BOOKING_PAYMENT_FAILED, saved);
        return saved;
    }

    private Booking confirmInternal(Booking booking) {
        if (booking.getStatus() != BookingStatus.DRAFT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only DRAFT can be confirmed");
        }

        if (booking.getPaymentStatus() != PaymentStatus.DEPOSITED
            && booking.getPaymentStatus() != PaymentStatus.PAID) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deposit required before confirm");
        }

        validateTimeRange(booking.getStartTime(), booking.getEndTime());
        validateSlotAlignment(booking.getStartTime(), booking.getEndTime());

        boolean overlap = bookingRepository.existsOverlapExcluding(
            booking.getCourt().getId(),
            booking.getStartTime(),
            booking.getEndTime(),
            activeStatuses(),
            booking.getId()
        );

        if (overlap) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Time slot not available");
        }

        booking.setStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(booking);
        bookingOutboxService.enqueue(BookingEventType.BOOKING_CONFIRMED, saved);
        return saved;
    }

    private void validateTimeRange(OffsetDateTime start, OffsetDateTime end) {
        if (start == null || end == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start/end time required");
        }
        if (!end.isAfter(start)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time range");
        }
    }

    private void validateSlotAlignment(OffsetDateTime start, OffsetDateTime end) {
        if (start.getMinute() % SLOT_MINUTES != 0 || end.getMinute() % SLOT_MINUTES != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Time must align to 30-minute slots");
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes % SLOT_MINUTES != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duration must be a multiple of 30 minutes");
        }
    }

    private void validateQueryRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid query range");
        }
    }

    private OffsetDateTime normalizeToUtc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private boolean isTerminalStatus(BookingStatus status) {
        return status == BookingStatus.CANCELED
            || status == BookingStatus.COMPLETED
            || status == BookingStatus.FAILED_TIMEOUT;
    }

    private OffsetDateTime toResponseOffset(OffsetDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZoneSameInstant(responseZoneId).toOffsetDateTime();
    }

    private BigDecimal calcDepositRequired(BigDecimal priceTotal) {
        return priceTotal.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
    }

    private List<BookingStatus> activeStatuses() {
        return List.of(BookingStatus.DRAFT, BookingStatus.CONFIRMED, BookingStatus.IN_PROGRESS);
    }

    private BatchBookingActionResponse toBatchActionResponse(List<Booking> bookings) {
        List<BookingResponse> responses = bookings.stream().map(this::toResponse).toList();
        BigDecimal totalPrice = bookings.stream()
            .map(Booking::getPriceTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDepositRequired = bookings.stream()
            .map(Booking::getDepositRequired)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDepositPaid = bookings.stream()
            .map(Booking::getDepositPaid)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new BatchBookingActionResponse(responses, totalPrice, totalDepositRequired, totalDepositPaid);
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
            booking.getId(),
            booking.getCourt() != null ? booking.getCourt().getId() : null,
            booking.getCustomerId(),
            booking.getStatus(),
            booking.getPaymentStatus(),
            toResponseOffset(booking.getStartTime()),
            toResponseOffset(booking.getEndTime()),
            booking.getPriceTotal(),
            booking.getDepositRequired(),
            booking.getDepositPaid()
        );
    }
}
