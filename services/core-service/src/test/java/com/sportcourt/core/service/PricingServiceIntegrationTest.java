package com.sportcourt.core.service;

import com.sportcourt.core.domain.Court;
import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.domain.enums.CustomerTier;
import com.sportcourt.core.domain.enums.PricingDayType;
import com.sportcourt.core.domain.enums.PricingRuleCustomerTier;
import com.sportcourt.core.domain.enums.SportType;
import com.sportcourt.core.dto.CourtCreateRequest;
import com.sportcourt.core.dto.PricingQuoteResponse;
import com.sportcourt.core.dto.PricingRuleCreateRequest;
import com.sportcourt.core.dto.PricingRuleResponse;
import com.sportcourt.core.dto.VenueCreateRequest;
import com.sportcourt.core.repository.CourtRepository;
import com.sportcourt.core.repository.PricingRuleRepository;
import com.sportcourt.core.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PricingServiceIntegrationTest {

    private static final ZoneOffset PLUS_7 = ZoneOffset.ofHours(7);

    @Autowired
    private PricingService pricingService;

    @Autowired
    private VenueService venueService;

    @Autowired
    private CourtService courtService;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private PricingRuleRepository pricingRuleRepository;

    @BeforeEach
    void setUp() {
        pricingRuleRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void quote_shouldReturnTotalFromRules() {
        Court court = createCourt("Court-Quote");
        pricingService.createRule(new PricingRuleCreateRequest(
            court.getId(),
            "Weekday morning",
            PricingDayType.WEEKDAY,
            LocalTime.of(8, 0),
            LocalTime.of(12, 0),
            PricingRuleCustomerTier.ALL,
            money("300000"),
            10
        ));

        PricingQuoteResponse quote = pricingService.quote(
            court.getId(),
            time(2026, 3, 30, 8, 0),
            time(2026, 3, 30, 10, 0),
            CustomerTier.STANDARD
        );

        assertThat(quote.totalPrice()).isEqualByComparingTo(money("600000.00"));
        assertThat(quote.slots()).hasSize(4);
        assertThat(quote.slots().get(0).ruleName()).isEqualTo("Weekday morning");
    }

    @Test
    void quote_shouldRejectWhenNoRule() {
        Court court = createCourt("Court-NoRule");
        assertThatThrownBy(() -> pricingService.quote(
            court.getId(),
            time(2026, 3, 30, 8, 0),
            time(2026, 3, 30, 9, 0),
            CustomerTier.MEMBER
        ))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRule_shouldListByCourt() {
        Court court = createCourt("Court-ListRule");
        PricingRuleResponse created = pricingService.createRule(new PricingRuleCreateRequest(
            court.getId(),
            "Weekend VIP",
            PricingDayType.WEEKEND,
            LocalTime.of(18, 0),
            LocalTime.of(22, 0),
            PricingRuleCustomerTier.VIP,
            money("500000"),
            20
        ));

        assertThat(created.courtId()).isEqualTo(court.getId());
        assertThat(pricingService.listRules(court.getId())).hasSize(1);
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
