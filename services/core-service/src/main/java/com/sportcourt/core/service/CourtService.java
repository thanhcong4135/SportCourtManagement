package com.sportcourt.core.service;

import com.sportcourt.core.domain.Court;
import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.dto.CourtCreateRequest;
import com.sportcourt.core.repository.CourtRepository;
import com.sportcourt.core.repository.VenueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class CourtService {

    private final CourtRepository courtRepository;
    private final VenueRepository venueRepository;

    public CourtService(CourtRepository courtRepository, VenueRepository venueRepository) {
        this.courtRepository = courtRepository;
        this.venueRepository = venueRepository;
    }

    public Court create(CourtCreateRequest req) {
        Venue venue = venueRepository.findById(req.venueId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));

        Court c = new Court();
        c.setVenue(venue);
        c.setName(req.name());
        c.setSportType(req.sportType());
        c.setActive(true);
        return courtRepository.save(c);
    }

    public List<Court> list(UUID venueId) {
        if (venueId == null) {
            return courtRepository.findAll();
        }
        return courtRepository.findByVenueId(venueId);
    }
}
