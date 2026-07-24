package com.sportcourt.notification.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sportcourt.notification.domain.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class NotificationEventFactory {

    private static final Set<String> SUPPORTED_SCHEMA_VERSIONS = Set.of("1.0", "1.1");
    private static final Set<String> BOOKING_EMAIL_ALLOWLIST = Set.of("BOOKING_CONFIRMED");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
        Pattern.CASE_INSENSITIVE
    );

    public List<NotificationEventCommand> fromBookingEvent(JsonNode event,
                                                            String topic,
                                                            String headerEventId) {
        validateSchemaVersion(event.path("schemaVersion").asText(null), "booking");

        String type = readType(event, "Booking");
        if (!isSupportedBookingType(type)) {
            return List.of();
        }

        UUID bookingId = readUuid(event, "bookingId", true);
        UUID customerId = readUuid(event, "customerId", false);
        String customerEmail = normalizeValidEmail(event.path("customerEmail").asText(null));
        String eventId = readEventId(event, headerEventId);
        String deepLink = "/account/bookings/" + bookingId;

        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "booking.events");
        metadata.put("bookingId", bookingId.toString());
        putIfNotBlank(metadata, "courtId", event.path("courtId").asText(null));
        putIfNotBlank(metadata, "status", event.path("status").asText(null));
        putIfNotBlank(metadata, "paymentStatus", event.path("paymentStatus").asText(null));
        putIfNotBlank(metadata, "startTime", event.path("startTime").asText(null));
        putIfNotBlank(metadata, "endTime", event.path("endTime").asText(null));

        TemplateContent content = bookingTemplate(type);
        List<NotificationEventCommand> commands = new ArrayList<>(2);
        if (customerId != null) {
            commands.add(command(
                topic, eventId, type, bookingId, null, customerId,
                NotificationChannel.IN_APP, customerId.toString(), content, deepLink, metadata
            ));
        }
        if (customerEmail != null && BOOKING_EMAIL_ALLOWLIST.contains(type)) {
            commands.add(command(
                topic, eventId, type, bookingId, null, customerId,
                NotificationChannel.EMAIL, customerEmail, content, deepLink, metadata
            ));
        }
        return List.copyOf(commands);
    }

    public List<NotificationEventCommand> fromPaymentEvent(JsonNode event,
                                                            String topic,
                                                            String headerEventId) {
        validateSchemaVersion(event.path("schemaVersion").asText(null), "payment");

        String type = readType(event, "Payment");
        if (!isSupportedPaymentType(type)) {
            return List.of();
        }

        UUID paymentId = readUuid(event, "paymentId", true);
        UUID bookingId = readUuid(event, "bookingId", true);
        UUID customerId = readUuid(event, "customerId", false);
        String eventId = readEventId(event, headerEventId);
        String deepLink = "/account/bookings/" + bookingId;

        Map<String, String> metadata = new HashMap<>();
        metadata.put("source", "payment.events");
        metadata.put("paymentId", paymentId.toString());
        metadata.put("bookingId", bookingId.toString());
        if (customerId != null) {
            metadata.put("customerId", customerId.toString());
        }
        putIfNotBlank(metadata, "providerReference", event.path("providerReference").asText(null));

        TemplateContent content = paymentTemplate(type);
        List<NotificationEventCommand> commands = new ArrayList<>(2);
        if (customerId != null) {
            commands.add(command(
                topic, eventId, type, bookingId, paymentId, customerId,
                NotificationChannel.IN_APP, customerId.toString(), content, deepLink, metadata
            ));
        }
        return List.copyOf(commands);
    }

    private NotificationEventCommand command(String topic,
                                             String eventId,
                                             String type,
                                             UUID bookingId,
                                             UUID paymentId,
                                             UUID customerId,
                                             NotificationChannel channel,
                                             String recipient,
                                             TemplateContent content,
                                             String deepLink,
                                             Map<String, String> metadata) {
        return new NotificationEventCommand(
            topic,
            eventId,
            type,
            bookingId,
            paymentId,
            customerId,
            eventId,
            channel,
            recipient,
            type,
            content.title(),
            deepLink,
            content.message(),
            metadata
        );
    }

    private TemplateContent bookingTemplate(String type) {
        return switch (type) {
            case "BOOKING_DRAFT_CREATED" -> new TemplateContent(
                "Đã tạo yêu cầu đặt sân",
                "Yêu cầu đặt sân của bạn đã được tạo và đang chờ xác nhận."
            );
            case "BOOKING_CONFIRMED" -> new TemplateContent(
                "Đặt sân thành công",
                "Booking của bạn đã được xác nhận thành công."
            );
            case "BOOKING_CANCELED" -> new TemplateContent(
                "Booking đã bị hủy",
                "Booking của bạn đã được hủy."
            );
            case "BOOKING_IN_PROGRESS" -> new TemplateContent(
                "Phiên chơi đã bắt đầu",
                "Phiên chơi của bạn đã bắt đầu."
            );
            case "BOOKING_COMPLETED" -> new TemplateContent(
                "Phiên chơi đã hoàn thành",
                "Phiên chơi của bạn đã hoàn thành."
            );
            case "BOOKING_DEPOSITED" -> new TemplateContent(
                "Đã nhận tiền đặt cọc",
                "SportCourt đã nhận tiền đặt cọc cho booking của bạn."
            );
            case "BOOKING_PAYMENT_FAILED" -> new TemplateContent(
                "Thanh toán booking thất bại",
                "Thanh toán cho booking chưa thành công. Vui lòng kiểm tra và thử lại."
            );
            default -> throw new IllegalArgumentException("Unsupported booking notification type: " + type);
        };
    }

    private TemplateContent paymentTemplate(String type) {
        return switch (type) {
            case "DEPOSIT_SUCCEEDED" -> new TemplateContent(
                "Thanh toán đặt cọc thành công",
                "Khoản đặt cọc của bạn đã được thanh toán thành công."
            );
            case "DEPOSIT_FAILED" -> new TemplateContent(
                "Thanh toán đặt cọc thất bại",
                "Thanh toán đặt cọc chưa thành công. Vui lòng kiểm tra và thử lại."
            );
            default -> throw new IllegalArgumentException("Unsupported payment notification type: " + type);
        };
    }

    private String readType(JsonNode event, String domain) {
        String type = event.path("type").asText(null);
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(domain + " event missing type");
        }
        return type;
    }

    private void validateSchemaVersion(String schemaVersion, String domain) {
        if (schemaVersion == null || schemaVersion.isBlank()) {
            return;
        }
        if (!SUPPORTED_SCHEMA_VERSIONS.contains(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported " + domain + " event schemaVersion: " + schemaVersion);
        }
    }

    private boolean isSupportedBookingType(String type) {
        return "BOOKING_DRAFT_CREATED".equals(type)
            || "BOOKING_CONFIRMED".equals(type)
            || "BOOKING_CANCELED".equals(type)
            || "BOOKING_IN_PROGRESS".equals(type)
            || "BOOKING_COMPLETED".equals(type)
            || "BOOKING_DEPOSITED".equals(type)
            || "BOOKING_PAYMENT_FAILED".equals(type);
    }

    private boolean isSupportedPaymentType(String type) {
        return "DEPOSIT_SUCCEEDED".equals(type) || "DEPOSIT_FAILED".equals(type);
    }

    private UUID readUuid(JsonNode event, String field, boolean required) {
        String value = event.path(field).asText(null);
        if (value == null || value.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("Event missing " + field);
            }
            return null;
        }
        return UUID.fromString(value);
    }

    private String readEventId(JsonNode event, String headerEventId) {
        String eventId = event.path("eventId").asText(null);
        if (eventId != null && !eventId.isBlank()) {
            return eventId;
        }
        if (headerEventId != null && !headerEventId.isBlank()) {
            return headerEventId;
        }
        throw new IllegalArgumentException("Event missing eventId");
    }

    private String normalizeValidEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 128 || !EMAIL_PATTERN.matcher(normalized).matches()) {
            return null;
        }
        return normalized;
    }

    private void putIfNotBlank(Map<String, String> metadata, String key, String value) {
        if (value != null && !value.isBlank()) {
            metadata.put(key, value);
        }
    }

    private record TemplateContent(String title, String message) {
    }
}
