package com.sportcourt.core.service;

import com.sportcourt.core.domain.Venue;
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
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class VenueServiceIntegrationTest {

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
    void create_shouldPersistVenueWithCreatedAt() {
        VenueCreateRequest req = new VenueCreateRequest("Arena A", "123 Test Street");

        Venue created = venueService.create(req);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Arena A");
        assertThat(created.getAddress()).isEqualTo("123 Test Street");
        assertThat(created.getCreatedAt()).isNotNull();
        assertThat(venueRepository.count()).isEqualTo(1);
    }

    @Test
    void list_shouldReturnAllSavedVenues() {
        venueService.create(new VenueCreateRequest("Arena A", "Address A"));
        venueService.create(new VenueCreateRequest("Arena B", "Address B"));

        List<Venue> venues = venueService.list();

        assertThat(venues).hasSize(2);
        assertThat(venues).extracting(Venue::getName).containsExactlyInAnyOrder("Arena A", "Arena B");
    }
}
