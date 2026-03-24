package com.sportcourt.core.service;

import com.sportcourt.core.domain.ConsumedEvent;
import com.sportcourt.core.repository.ConsumedEventRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class IdempotentEventService {

    private final ConsumedEventRepository consumedEventRepository;

    public IdempotentEventService(ConsumedEventRepository consumedEventRepository) {
        this.consumedEventRepository = consumedEventRepository;
    }

    @Transactional
    public boolean tryMarkProcessed(String eventId, String topic) {
        ConsumedEvent consumedEvent = new ConsumedEvent();
        consumedEvent.setEventId(eventId);
        consumedEvent.setTopic(topic);
        consumedEvent.setConsumedAt(OffsetDateTime.now(ZoneOffset.UTC));

        try {
            consumedEventRepository.saveAndFlush(consumedEvent);
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}
