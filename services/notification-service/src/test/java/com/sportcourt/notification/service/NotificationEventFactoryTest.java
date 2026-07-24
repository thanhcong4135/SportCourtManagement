package com.sportcourt.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.notification.domain.NotificationChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationEventFactoryTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationEventFactory factory = new NotificationEventFactory();

    @Test
    void schemaOnePointZero_shouldCreateInAppOnly() throws Exception {
        var event = objectMapper.readTree("""
            {
              "schemaVersion":"1.0",
              "eventId":"evt-1",
              "type":"BOOKING_CONFIRMED",
              "bookingId":"11111111-1111-1111-1111-111111111111",
              "customerId":"22222222-2222-2222-2222-222222222222"
            }
            """);

        var commands = factory.fromBookingEvent(event, "booking.events", null);

        assertThat(commands).hasSize(1);
        NotificationEventCommand command = commands.get(0);
        assertThat(command.channel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(command.title()).isEqualTo("Đặt sân thành công");
        assertThat(command.deepLink()).isEqualTo("/account/bookings/11111111-1111-1111-1111-111111111111");
        assertThat(command.message()).doesNotContain("trace", "stack");
    }

    @Test
    void schemaOnePointOneImportantEvent_shouldCreateInAppAndEmail() throws Exception {
        var event = objectMapper.readTree("""
            {
              "schemaVersion":"1.1",
              "eventId":"evt-2",
              "type":"BOOKING_CONFIRMED",
              "bookingId":"11111111-1111-1111-1111-111111111111",
              "customerId":"22222222-2222-2222-2222-222222222222",
              "customerEmail":" Customer@Example.COM "
            }
            """);

        var commands = factory.fromBookingEvent(event, "booking.events", null);

        assertThat(commands).extracting(NotificationEventCommand::channel)
            .containsExactly(NotificationChannel.IN_APP, NotificationChannel.EMAIL);
        assertThat(commands.get(1).recipient()).isEqualTo("customer@example.com");
    }

    @Test
    void bookingDeposited_shouldCreateInAppOnly() throws Exception {
        var event = objectMapper.readTree("""
            {
              "schemaVersion":"1.1",
              "eventId":"evt-3",
              "type":"BOOKING_DEPOSITED",
              "bookingId":"11111111-1111-1111-1111-111111111111",
              "customerId":"22222222-2222-2222-2222-222222222222",
              "customerEmail":"customer@example.com"
            }
            """);

        assertThat(factory.fromBookingEvent(event, "booking.events", null))
            .extracting(NotificationEventCommand::channel)
            .containsExactly(NotificationChannel.IN_APP);
    }

    @Test
    void depositSucceeded_shouldCreateInAppOnly() throws Exception {
        var event = objectMapper.readTree("""
            {
              "schemaVersion":"1.1",
              "eventId":"evt-4",
              "type":"DEPOSIT_SUCCEEDED",
              "paymentId":"33333333-3333-3333-3333-333333333333",
              "bookingId":"11111111-1111-1111-1111-111111111111",
              "customerId":"22222222-2222-2222-2222-222222222222",
              "customerEmail":"customer@example.com"
            }
            """);

        assertThat(factory.fromPaymentEvent(event, "payment.events", null))
            .extracting(NotificationEventCommand::channel)
            .containsExactly(NotificationChannel.IN_APP);
    }

    @Test
    void invalidEmail_shouldBeIgnored() throws Exception {
        var event = objectMapper.readTree("""
            {
              "schemaVersion":"1.1",
              "eventId":"evt-5",
              "type":"BOOKING_CONFIRMED",
              "bookingId":"11111111-1111-1111-1111-111111111111",
              "customerId":"22222222-2222-2222-2222-222222222222",
              "customerEmail":"not-an-email"
            }
            """);

        assertThat(factory.fromBookingEvent(event, "booking.events", null))
            .extracting(NotificationEventCommand::channel)
            .containsExactly(NotificationChannel.IN_APP);
    }
}
