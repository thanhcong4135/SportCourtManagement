package com.sportcourt.payment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.payment.domain.PaymentTransaction;
import com.sportcourt.payment.domain.enums.PaymentTransactionStatus;
import com.sportcourt.payment.dto.PaymentCallbackRequest;
import com.sportcourt.payment.dto.PaymentTransactionResponse;
import com.sportcourt.payment.repository.PaymentTransactionRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
    "kafka.consumer.booking.enabled=true",
    "outbox.publisher.enabled=true",
    "outbox.publisher.fixed-delay-ms=100"
})
class PaymentKafkaE2ETest {

    @Container
    static final ConfluentKafkaContainer KAFKA =
        new ConfluentKafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"));

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
    }

    @Test
    void bookingEventThenCallback_shouldCreatePaymentAndPublishPaymentEvent() throws Exception {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();

        try (Consumer<String, String> consumer = createPaymentEventConsumer()) {
            consumer.subscribe(List.of("payment.events"));

            String bookingEventPayload = """
                {
                  "eventId": "%s",
                  "type": "BOOKING_DRAFT_CREATED",
                  "bookingId": "%s",
                  "customerId": "%s",
                  "priceTotal": 400000.00
                }
                """.formatted(eventId, bookingId, customerId);

            kafkaTemplate.send("booking.events", bookingId.toString(), bookingEventPayload)
                .get(10, TimeUnit.SECONDS);

            PaymentTransaction created = waitForPayment(bookingId, Duration.ofSeconds(15));
            assertThat(created.getCustomerId()).isEqualTo(customerId);
            assertThat(created.getAmount()).isEqualByComparingTo("200000.00");
            assertThat(created.getStatus()).isEqualTo(PaymentTransactionStatus.PENDING);
            assertThat(created.getIdempotencyKey()).isEqualTo(eventId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Payment-Callback-Secret", "test-callback-secret");
            ResponseEntity<PaymentTransactionResponse> callbackResponse = restTemplate.postForEntity(
                endpointUrl("/api/payments/callback"),
                new HttpEntity<>(new PaymentCallbackRequest(created.getId(), "provider-ref-e2e", true, null), headers),
                PaymentTransactionResponse.class
            );

            assertThat(callbackResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(callbackResponse.getBody()).isNotNull();
            assertThat(callbackResponse.getBody().status()).isEqualTo(PaymentTransactionStatus.SUCCESS);
            assertThat(callbackResponse.getBody().providerReference()).isEqualTo("provider-ref-e2e");

            ConsumerRecord<String, String> published = KafkaTestUtils.getSingleRecord(
                consumer,
                "payment.events",
                Duration.ofSeconds(15)
            );
            assertThat(published.key()).isEqualTo(bookingId.toString());

            JsonNode paymentEvent = objectMapper.readTree(published.value());
            assertThat(paymentEvent.path("type").asText()).isEqualTo("DEPOSIT_SUCCEEDED");
            assertThat(paymentEvent.path("bookingId").asText()).isEqualTo(bookingId.toString());
            assertThat(paymentEvent.path("customerId").asText()).isEqualTo(customerId.toString());
        }
    }

    private Consumer<String, String> createPaymentEventConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
            KAFKA.getBootstrapServers(),
            "payment-e2e-" + UUID.randomUUID(),
            "false"
        );
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }

    private PaymentTransaction waitForPayment(UUID bookingId, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            List<PaymentTransaction> transactions = paymentTransactionRepository.findByBookingIdOrderByRequestedAtDesc(bookingId);
            if (!transactions.isEmpty()) {
                return transactions.get(0);
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting payment transaction", ex);
            }
        }
        fail("Timed out waiting payment transaction for booking " + bookingId);
        return null;
    }

    private String endpointUrl(String path) {
        return "http://localhost:" + port + path;
    }
}
