package com.sportcourt.payment.dlq;

import com.sportcourt.payment.dto.DeadLetterEventResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadLetterEventServiceTest {

    @Test
    void capture_shouldPersistDeadLetterRecord() {
        DeadLetterEventRepository repository = mock(DeadLetterEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DeadLetterEventService service = new DeadLetterEventService(repository, kafkaTemplate, ".dlq", 5);

        when(repository.existsByDeadLetterTopicAndKafkaPartitionAndKafkaOffset("booking.events.dlq", 1, 42L))
            .thenReturn(false);

        Map<String, Object> headers = new HashMap<>();
        headers.put(KafkaHeaders.DLT_ORIGINAL_TOPIC, "booking.events");
        headers.put(KafkaHeaders.RECEIVED_KEY, "booking-1");
        headers.put("event-id", "event-1");
        headers.put(KafkaHeaders.DLT_EXCEPTION_MESSAGE, "schema mismatch");

        service.capture("booking.events.dlq", 1, 42L, "{\"sample\":true}", headers);

        verify(repository).save(any(DeadLetterEvent.class));
    }

    @Test
    void capture_shouldSkipWhenAlreadyRecorded() {
        DeadLetterEventRepository repository = mock(DeadLetterEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DeadLetterEventService service = new DeadLetterEventService(repository, kafkaTemplate, ".dlq", 5);

        when(repository.existsByDeadLetterTopicAndKafkaPartitionAndKafkaOffset("booking.events.dlq", 1, 42L))
            .thenReturn(true);

        service.capture("booking.events.dlq", 1, 42L, "{}", Map.of());

        verify(repository, never()).save(any(DeadLetterEvent.class));
    }

    @Test
    void replay_shouldMarkReplayedWhenPublishSucceeds() {
        DeadLetterEventRepository repository = mock(DeadLetterEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DeadLetterEventService service = new DeadLetterEventService(repository, kafkaTemplate, ".dlq", 5);

        UUID id = UUID.randomUUID();
        DeadLetterEvent event = sampleEvent(id);
        when(repository.findById(id)).thenReturn(Optional.of(event));
        when(repository.save(any(DeadLetterEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kafkaTemplate.send(any(Message.class))).thenReturn(CompletableFuture.completedFuture(null));

        DeadLetterEventResponse response = service.replay(id);

        assertThat(response.status()).isEqualTo(DeadLetterEventStatus.REPLAYED);
        assertThat(response.replayCount()).isEqualTo(1);
    }

    @Test
    void replay_shouldMarkFailedWhenPublishThrows() {
        DeadLetterEventRepository repository = mock(DeadLetterEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DeadLetterEventService service = new DeadLetterEventService(repository, kafkaTemplate, ".dlq", 5);

        UUID id = UUID.randomUUID();
        DeadLetterEvent event = sampleEvent(id);
        when(repository.findById(id)).thenReturn(Optional.of(event));
        when(repository.save(any(DeadLetterEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(kafkaTemplate.send(any(Message.class))).thenThrow(new RuntimeException("publish failed"));

        DeadLetterEventResponse response = service.replay(id);

        assertThat(response.status()).isEqualTo(DeadLetterEventStatus.FAILED);
        assertThat(response.replayCount()).isEqualTo(1);
        assertThat(response.failureReason()).contains("publish failed");
    }

    @Test
    void replay_shouldRejectWhenReplayLimitExceeded() {
        DeadLetterEventRepository repository = mock(DeadLetterEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        DeadLetterEventService service = new DeadLetterEventService(repository, kafkaTemplate, ".dlq", 1);

        UUID id = UUID.randomUUID();
        DeadLetterEvent event = sampleEvent(id);
        event.setReplayCount(1);
        event.setStatus(DeadLetterEventStatus.FAILED);
        when(repository.findById(id)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> service.replay(id))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException statusException = (ResponseStatusException) ex;
                assertThat(statusException.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            });
    }

    private DeadLetterEvent sampleEvent(UUID id) {
        DeadLetterEvent event = new DeadLetterEvent();
        event.setId(id);
        event.setSourceTopic("booking.events");
        event.setDeadLetterTopic("booking.events.dlq");
        event.setKafkaPartition(0);
        event.setKafkaOffset(10);
        event.setEventKey("booking-1");
        event.setEventId("event-1");
        event.setPayload("{\"schemaVersion\":\"1.0\"}");
        event.setStatus(DeadLetterEventStatus.RECEIVED);
        event.setReplayCount(0);
        event.setReceivedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return event;
    }
}
