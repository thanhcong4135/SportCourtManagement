package com.sportcourt.core.service;

import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.dto.VenueCreateRequest;
import com.sportcourt.core.repository.VenueRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class VenueService {

    private final VenueRepository venueRepository;

    public VenueService(VenueRepository venueRepository) {
        this.venueRepository = venueRepository;
    }

    public Venue create(VenueCreateRequest req) {
        Venue v = new Venue();
        v.setName(req.name());
        v.setAddress(req.address());
        v.setCreatedAt(OffsetDateTime.now());
        return venueRepository.save(v);
    }

    public List<Venue> list() {
        return venueRepository.findAll();
    }
}
