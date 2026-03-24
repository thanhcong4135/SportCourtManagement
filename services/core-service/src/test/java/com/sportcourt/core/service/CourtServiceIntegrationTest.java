package com.sportcourt.core.service;

import com.sportcourt.core.domain.Court;
import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.domain.enums.SportType;
import com.sportcourt.core.dto.CourtCreateRequest;
import com.sportcourt.core.dto.VenueCreateRequest;
import com.sportcourt.core.event.BookingEventPublisher;
import com.sportcourt.core.repository.BookingRepository;
import com.sportcourt.core.repository.CourtRepository;
import com.sportcourt.core.repository.VenueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class CourtServiceIntegrationTest {

    @Autowired
    private CourtService courtService;

    @Autowired
    private VenueService venueService;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private CourtRepository courtRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @MockBean
    private BookingEventPublisher bookingEventPublisher;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        courtRepository.deleteAll();
        venueRepository.deleteAll();
    }

    @Test
    void create_shouldPersistCourtWhenVenueExists() {
        Venue venue = venueService.create(new VenueCreateRequest("Arena A", "Address A"));
        CourtCreateRequest req = new CourtCreateRequest(venue.getId(), "Court 1", SportType.BADMINTON);

        Court created = courtService.create(req);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getVenue().getId()).isEqualTo(venue.getId());
        assertThat(created.getName()).isEqualTo("Court 1");
        assertThat(created.getSportType()).isEqualTo(SportType.BADMINTON);
        assertThat(created.isActive()).isTrue();
    }

    @Test
    void create_shouldThrowNotFoundWhenVenueMissing() {
        CourtCreateRequest req = new CourtCreateRequest(UUID.randomUUID(), "Court X", SportType.FOOTBALL);

        assertThatThrownBy(() -> courtService.create(req))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void list_shouldFilterByVenueId() {
        Venue venueA = venueService.create(new VenueCreateRequest("Arena A", "Address A"));
        Venue venueB = venueService.create(new VenueCreateRequest("Arena B", "Address B"));

        courtService.create(new CourtCreateRequest(venueA.getId(), "A-1", SportType.BADMINTON));
        courtService.create(new CourtCreateRequest(venueB.getId(), "B-1", SportType.PICKLEBALL));

        List<Court> filtered = courtService.list(venueA.getId());

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getName()).isEqualTo("A-1");
        assertThat(filtered.get(0).getVenue().getId()).isEqualTo(venueA.getId());
    }
}
