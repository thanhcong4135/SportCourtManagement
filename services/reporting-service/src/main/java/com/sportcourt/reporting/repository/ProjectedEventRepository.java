package com.sportcourt.reporting.repository;

import com.sportcourt.reporting.domain.ProjectedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProjectedEventRepository extends JpaRepository<ProjectedEvent, UUID> {

    Optional<ProjectedEvent> findByEventIdAndSourceTopic(String eventId, String sourceTopic);
}
