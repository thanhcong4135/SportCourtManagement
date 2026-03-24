package com.sportcourt.core.service;

import com.sportcourt.core.domain.ConsumedEvent;
import com.sportcourt.core.repository.ConsumedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdempotentEventServiceTest {

    @Test
    void tryMarkProcessed_shouldReturnTrueForNewEvent() {
        ConsumedEventRepository repository = mock(ConsumedEventRepository.class);
        when(repository.saveAndFlush(any(ConsumedEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        IdempotentEventService service = new IdempotentEventService(repository);

        boolean processed = service.tryMarkProcessed("evt-1", "booking.events");

        assertThat(processed).isTrue();
    }

    @Test
    void tryMarkProcessed_shouldReturnFalseForDuplicateEvent() {
        ConsumedEventRepository repository = mock(ConsumedEventRepository.class);
        doThrow(new DataIntegrityViolationException("duplicate")).when(repository).saveAndFlush(any(ConsumedEvent.class));
        IdempotentEventService service = new IdempotentEventService(repository);

        boolean processed = service.tryMarkProcessed("evt-1", "booking.events");

        assertThat(processed).isFalse();
    }
}
