package com.sportcourt.core.repository;

import com.sportcourt.core.domain.ConsumedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConsumedEventRepository extends JpaRepository<ConsumedEvent, UUID> {
}
