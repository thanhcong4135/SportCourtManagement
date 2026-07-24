package com.sportcourt.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.notification.domain.NotificationMessage;
import com.sportcourt.notification.dto.NotificationResponse;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NotificationResponseMapper {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public NotificationResponseMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NotificationResponse toResponse(NotificationMessage message) {
        return new NotificationResponse(
            message.getId(),
            message.getStatus().name(),
            message.getRecipient(),
            message.getChannel(),
            message.getTemplateCode(),
            message.getTitle(),
            message.getDeepLink(),
            message.getMessage(),
            toMetadataMap(message.getMetadataJson()),
            message.getBookingId(),
            message.getPaymentId(),
            message.getCustomerId(),
            message.getSourceEventId(),
            message.getSourceEventType(),
            message.getTraceId(),
            message.getRetryCount(),
            message.getLastError(),
            message.getCreatedAt(),
            message.getSentAt(),
            message.getReadAt(),
            message.getReadAt() == null,
            message.getUpdatedAt()
        );
    }

    private Map<String, String> toMetadataMap(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(metadataJson, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse notification metadata", ex);
        }
    }
}
