package com.sportcourt.core.service;

import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.domain.VenueImage;
import com.sportcourt.core.dto.VenueCreateRequest;
import com.sportcourt.core.dto.VenueImageCreateRequest;
import com.sportcourt.core.dto.VenueImageResponse;
import com.sportcourt.core.dto.VenueImageUpdateRequest;
import com.sportcourt.core.dto.VenueResponse;
import com.sportcourt.core.dto.VenueUpdateRequest;
import com.sportcourt.core.mapper.CoreApiMapper;
import com.sportcourt.core.repository.VenueImageRepository;
import com.sportcourt.core.repository.VenueRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class VenueService {

    private final VenueRepository venueRepository;
    private final VenueImageRepository venueImageRepository;

    public VenueService(VenueRepository venueRepository, VenueImageRepository venueImageRepository) {
        this.venueRepository = venueRepository;
        this.venueImageRepository = venueImageRepository;
    }

    @Transactional
    public Venue create(VenueCreateRequest req) {
        Venue v = new Venue();
        v.setName(req.name());
        v.setAddress(req.address());
        applyMetadata(v, req.description(), firstPresent(req.coverImageUrl(), req.imageUrl()), req.phone(), req.openingTime(), req.closingTime(), req.latitude(), req.longitude());
        v.setCreatedAt(OffsetDateTime.now());
        return venueRepository.save(v);
    }

    @Transactional(readOnly = true)
    public List<Venue> list() {
        return venueRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<VenueResponse> listResponses() {
        return venueRepository.findAll()
            .stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public Venue update(UUID id, VenueUpdateRequest req) {
        Venue venue = venueRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
        venue.setName(req.name());
        venue.setAddress(req.address());
        applyMetadata(venue, req.description(), firstPresent(req.coverImageUrl(), req.imageUrl()), req.phone(), req.openingTime(), req.closingTime(), req.latitude(), req.longitude());
        return venueRepository.save(venue);
    }

    @Transactional(readOnly = true)
    public VenueResponse toResponse(Venue venue) {
        List<VenueImage> images = venueImageRepository.findByVenueIdOrderBySortOrderAscCreatedAtAsc(venue.getId());
        return CoreApiMapper.toVenueResponse(venue, images);
    }

    @Transactional(readOnly = true)
    public List<VenueImageResponse> listImages(UUID venueId) {
        ensureVenue(venueId);
        return venueImageRepository.findByVenueIdOrderBySortOrderAscCreatedAtAsc(venueId)
            .stream()
            .map(CoreApiMapper::toVenueImageResponse)
            .toList();
    }

    @Transactional
    public VenueImageResponse createImage(UUID venueId, VenueImageCreateRequest req) {
        Venue venue = ensureVenue(venueId);
        boolean shouldCover = Boolean.TRUE.equals(req.cover()) || normalize(venue.getCoverImageUrl()) == null;

        VenueImage image = new VenueImage();
        image.setVenue(venue);
        image.setImageUrl(normalizeRequired(req.imageUrl()));
        image.setAltText(normalize(req.altText()));
        image.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        image.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        if (shouldCover) {
            clearImageCovers(venueId);
            image.setCover(true);
            venue.setCoverImageUrl(image.getImageUrl());
        }

        VenueImage saved = venueImageRepository.save(image);
        venueRepository.save(venue);
        return CoreApiMapper.toVenueImageResponse(saved);
    }

    @Transactional
    public VenueImageResponse updateImage(UUID venueId, UUID imageId, VenueImageUpdateRequest req) {
        Venue venue = ensureVenue(venueId);
        VenueImage image = getVenueImage(venueId, imageId);
        image.setImageUrl(normalizeRequired(req.imageUrl()));
        image.setAltText(normalize(req.altText()));
        image.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        if (Boolean.TRUE.equals(req.cover())) {
            clearImageCovers(venueId);
            image.setCover(true);
            venue.setCoverImageUrl(image.getImageUrl());
            venueRepository.save(venue);
        } else if (image.isCover()) {
            venue.setCoverImageUrl(image.getImageUrl());
            venueRepository.save(venue);
        }
        return CoreApiMapper.toVenueImageResponse(venueImageRepository.save(image));
    }

    @Transactional
    public void deleteImage(UUID venueId, UUID imageId) {
        Venue venue = ensureVenue(venueId);
        VenueImage image = getVenueImage(venueId, imageId);
        boolean wasCover = image.isCover() || image.getImageUrl().equals(venue.getCoverImageUrl());
        venueImageRepository.delete(image);
        venueImageRepository.flush();

        if (wasCover) {
            List<VenueImage> remaining = venueImageRepository.findByVenueIdOrderBySortOrderAscCreatedAtAsc(venueId);
            if (remaining.isEmpty()) {
                venue.setCoverImageUrl(null);
            } else {
                VenueImage nextCover = remaining.get(0);
                clearImageCovers(venueId);
                nextCover.setCover(true);
                venue.setCoverImageUrl(nextCover.getImageUrl());
                venueImageRepository.save(nextCover);
            }
            venueRepository.save(venue);
        }
    }

    @Transactional
    public VenueImageResponse setCoverImage(UUID venueId, UUID imageId) {
        Venue venue = ensureVenue(venueId);
        VenueImage image = getVenueImage(venueId, imageId);
        clearImageCovers(venueId);
        image.setCover(true);
        venue.setCoverImageUrl(image.getImageUrl());
        venueRepository.save(venue);
        return CoreApiMapper.toVenueImageResponse(venueImageRepository.save(image));
    }

    private void applyMetadata(Venue venue,
                               String description,
                               String coverImageUrl,
                               String phone,
                               java.time.LocalTime openingTime,
                               java.time.LocalTime closingTime,
                               java.math.BigDecimal latitude,
                               java.math.BigDecimal longitude) {
        venue.setDescription(normalize(description));
        venue.setCoverImageUrl(normalize(coverImageUrl));
        venue.setPhone(normalize(phone));
        venue.setOpeningTime(openingTime);
        venue.setClosingTime(closingTime);
        venue.setLatitude(latitude);
        venue.setLongitude(longitude);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeRequired(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageUrl is required");
        }
        return normalized;
    }

    private String firstPresent(String primary, String fallback) {
        String normalizedPrimary = normalize(primary);
        return normalizedPrimary != null ? normalizedPrimary : normalize(fallback);
    }

    private Venue ensureVenue(UUID venueId) {
        return venueRepository.findById(venueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));
    }

    private VenueImage getVenueImage(UUID venueId, UUID imageId) {
        return venueImageRepository.findByIdAndVenueId(imageId, venueId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue image not found"));
    }

    private void clearImageCovers(UUID venueId) {
        List<VenueImage> images = venueImageRepository.findByVenueIdOrderBySortOrderAscCreatedAtAsc(venueId);
        images.forEach(image -> image.setCover(false));
        venueImageRepository.saveAll(images);
    }
}
