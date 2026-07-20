package com.sportcourt.core.service;

import com.sportcourt.core.domain.Product;
import com.sportcourt.core.domain.Venue;
import com.sportcourt.core.dto.ProductCreateRequest;
import com.sportcourt.core.dto.ProductResponse;
import com.sportcourt.core.repository.ProductRepository;
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
public class ProductService {

    private final ProductRepository productRepository;
    private final VenueRepository venueRepository;

    public ProductService(ProductRepository productRepository, VenueRepository venueRepository) {
        this.productRepository = productRepository;
        this.venueRepository = venueRepository;
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest req) {
        Venue venue = venueRepository.findById(req.venueId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venue not found"));

        Product product = new Product();
        product.setVenue(venue);
        product.setName(req.name().trim());
        product.setDescription(normalize(req.description()));
        product.setImageUrl(normalize(req.imageUrl()));
        product.setCategory(normalize(req.category()));
        product.setUnit(normalize(req.unit()));
        product.setUnitPrice(req.unitPrice());
        product.setActive(req.active() == null || req.active());
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        product.setCreatedAt(now);
        product.setUpdatedAt(now);

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> list(UUID venueId, Boolean activeOnly) {
        List<Product> products;
        if (venueId == null) {
            products = productRepository.findAll();
        } else if (Boolean.TRUE.equals(activeOnly)) {
            products = productRepository.findByVenueIdAndActive(venueId, true);
        } else {
            products = productRepository.findByVenueId(venueId);
        }
        return products.stream().map(this::toResponse).toList();
    }

    ProductResponse toResponse(Product product) {
        return new ProductResponse(
            product.getId(),
            product.getVenue().getId(),
            product.getName(),
            product.getDescription(),
            product.getImageUrl(),
            product.getCategory(),
            product.getUnit(),
            product.getUnitPrice(),
            product.isActive(),
            product.getCreatedAt(),
            product.getUpdatedAt()
        );
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
