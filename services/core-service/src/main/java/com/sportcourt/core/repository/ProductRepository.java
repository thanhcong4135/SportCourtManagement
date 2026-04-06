package com.sportcourt.core.repository;

import com.sportcourt.core.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    List<Product> findByVenueIdAndActive(UUID venueId, boolean active);

    List<Product> findByVenueId(UUID venueId);
}
