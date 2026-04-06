package com.sportcourt.core.service;

import com.sportcourt.core.api.PageResponse;
import com.sportcourt.core.domain.Court;
import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.domain.enums.SportType;
import com.sportcourt.core.dto.BookingDraftRequest;
import com.sportcourt.core.dto.BookingResponse;
import com.sportcourt.core.dto.CourtCreateRequest;
import com.sportcourt.core.dto.ProductCreateRequest;
import com.sportcourt.core.dto.ProductResponse;
import com.sportcourt.core.dto.SalesOrderCreateRequest;
import com.sportcourt.core.dto.SalesOrderItemRequest;
import com.sportcourt.core.dto.SalesOrderResponse;
import com.sportcourt.core.dto.VenueCreateRequest;
import com.sportcourt.core.repository.BookingActionIdempotencyRepository;
import com.sportcourt.core.repository.BookingRepository;
import com.sportcourt.core.repository.CourtRepository;
import com.sportcourt.core.repository.ProductRepository;
import com.sportcourt.core.repository.SalesOrderRepository;
import com.sportcourt.core.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class ProductAndSalesOrderIntegrationTest {

    private static final ZoneOffset PLUS_7 = ZoneOffset.ofHours(7);

    @Autowired
    private ProductService productService;

    @Autowired
    private SalesOrderService salesOrderService;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private VenueService venueService;

    @Autowired
    private CourtService courtService;

    @Autowired
    private SalesOrderRepository salesOrderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BookingActionIdempotencyRepository bookingActionIdempotencyRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private VenueRepository venueRepository;

    @BeforeEach
    void setUp() {
        salesOrderRepository.deleteAll();
        productRepository.deleteAll();
        bookingActionIdempotencyRepository.deleteAll();
        bookingRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void createProductAndList_shouldReturnSavedProduct() {
        Venue venue = venueService.create(new VenueCreateRequest("Venue-Products", "Address"));

        ProductResponse created = productService.create(new ProductCreateRequest(
            venue.getId(),
            "Nuoc suoi",
            money("10000"),
            true
        ));

        List<ProductResponse> products = productService.list(venue.getId(), true);
        assertThat(created.venueId()).isEqualTo(venue.getId());
        assertThat(products).hasSize(1);
        assertThat(products.get(0).name()).isEqualTo("Nuoc suoi");
    }

    @Test
    void createSalesOrder_withBookingId_shouldAttachBookingAndComputeTotal() {
        Court court = createCourt("Court-Order");
        UUID customerId = UUID.randomUUID();
        BookingResponse booking = bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            customerId,
            time(2026, 4, 1, 8, 0),
            time(2026, 4, 1, 10, 0),
            money("300000")
        ));

        ProductResponse water = productService.create(new ProductCreateRequest(
            court.getVenue().getId(),
            "Water",
            money("10000"),
            true
        ));
        ProductResponse shuttle = productService.create(new ProductCreateRequest(
            court.getVenue().getId(),
            "Shuttlecock",
            money("25000"),
            true
        ));

        SalesOrderResponse order = salesOrderService.create(new SalesOrderCreateRequest(
            booking.id(),
            null,
            customerId,
            List.of(
                new SalesOrderItemRequest(water.id(), 2),
                new SalesOrderItemRequest(shuttle.id(), 1)
            )
        ));

        assertThat(order.bookingId()).isEqualTo(booking.id());
        assertThat(order.totalAmount()).isEqualByComparingTo(money("45000"));
        assertThat(order.items()).hasSize(2);
    }

    @Test
    void listSalesOrders_shouldFilterByBookingId() {
        Court court = createCourt("Court-Order-List");
        UUID customerId = UUID.randomUUID();
        BookingResponse booking = bookingService.createDraft(new BookingDraftRequest(
            court.getId(),
            customerId,
            time(2026, 4, 2, 8, 0),
            time(2026, 4, 2, 10, 0),
            money("300000")
        ));

        ProductResponse item = productService.create(new ProductCreateRequest(
            court.getVenue().getId(),
            "Ball",
            money("50000"),
            true
        ));
        salesOrderService.create(new SalesOrderCreateRequest(
            booking.id(),
            null,
            customerId,
            List.of(new SalesOrderItemRequest(item.id(), 1))
        ));

        PageResponse<SalesOrderResponse> page = salesOrderService.list(
            booking.id(),
            null,
            customerId,
            null,
            null,
            PageRequest.of(0, 10)
        );

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).bookingId()).isEqualTo(booking.id());
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
