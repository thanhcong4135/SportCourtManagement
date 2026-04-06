package com.sportcourt.reporting.service;

import com.sportcourt.reporting.domain.ProjectedEvent;
import com.sportcourt.reporting.repository.ProjectedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class ProjectionEventService {

    private final ProjectedEventRepository projectedEventRepository;

    public ProjectionEventService(ProjectedEventRepository projectedEventRepository) {
        this.projectedEventRepository = projectedEventRepository;
    }

    @Transactional
    public boolean reserve(String eventId, String sourceTopic) {
        if (eventId == null || eventId.isBlank()) {
            return true;
        }
        if (projectedEventRepository.findByEventIdAndSourceTopic(eventId, sourceTopic).isPresent()) {
            return false;
        }

        ProjectedEvent projectedEvent = new ProjectedEvent();
        projectedEvent.setId(UUID.randomUUID());
        projectedEvent.setEventId(eventId);
        projectedEvent.setSourceTopic(sourceTopic);
        projectedEvent.setConsumedAt(OffsetDateTime.now(ZoneOffset.UTC));
        try {
            projectedEventRepository.save(projectedEvent);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}
