package com.sportcourt.payment.service;

import com.sportcourt.payment.domain.enums.PaymentTransactionStatus;
import com.sportcourt.payment.domain.enums.PaymentProvider;
import com.sportcourt.payment.dto.CreateDepositPaymentRequest;
import com.sportcourt.payment.dto.PaymentCallbackRequest;
import com.sportcourt.payment.dto.PaymentTransactionResponse;
import com.sportcourt.payment.event.PaymentOutboxService;
import com.sportcourt.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class PaymentServiceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentTransactionRepository paymentTransactionRepository;

    @MockBean
    private PaymentOutboxService paymentOutboxService;

    @BeforeEach
    void setUp() {
        paymentTransactionRepository.deleteAll();
    }

    @Test
    void initiateDeposit_shouldCreatePendingPayment() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        PaymentTransactionResponse response = paymentService.initiateDeposit(new CreateDepositPaymentRequest(
            bookingId,
            customerId,
            new BigDecimal("200000"),
            "vnd",
            "idempo-1"
        ));

        assertThat(response.bookingId()).isEqualTo(bookingId);
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.status()).isEqualTo(PaymentTransactionStatus.PENDING);
        assertThat(response.provider()).isEqualTo(PaymentProvider.MOCK);
        assertThat(response.checkoutUrl()).contains("paymentId=" + response.id());
    }

    @Test
    void initiateDeposit_shouldBeIdempotentByIdempotencyKey() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        CreateDepositPaymentRequest request = new CreateDepositPaymentRequest(
            bookingId,
            customerId,
            new BigDecimal("200000"),
            "VND",
            "idempo-2"
        );

        PaymentTransactionResponse first = paymentService.initiateDeposit(request);
        PaymentTransactionResponse second = paymentService.initiateDeposit(request);

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(paymentTransactionRepository.count()).isEqualTo(1);
    }

    @Test
    void callbackSuccess_shouldMarkSuccessAndPublishEvent() {
        PaymentTransactionResponse payment = paymentService.initiateDeposit(new CreateDepositPaymentRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("150000"),
            "VND",
            "idempo-3"
        ));

        PaymentTransactionResponse updated = paymentService.applyCallback(new PaymentCallbackRequest(
            payment.id(),
            "provider-ref-123",
            true,
            null
        ));

        assertThat(updated.status()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(updated.providerReference()).isEqualTo("provider-ref-123");
        assertThat(updated.completedAt()).isNotNull();
        verify(paymentOutboxService, times(1)).enqueueDepositResult(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void callbackFailed_shouldMarkFailedAndPublishEvent() {
        PaymentTransactionResponse payment = paymentService.initiateDeposit(new CreateDepositPaymentRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("100000"),
            "VND",
            "idempo-4"
        ));

        PaymentTransactionResponse updated = paymentService.applyCallback(new PaymentCallbackRequest(
            payment.id(),
            "provider-ref-124",
            false,
            "card declined"
        ));

        assertThat(updated.status()).isEqualTo(PaymentTransactionStatus.FAILED);
        assertThat(updated.failureReason()).isEqualTo("card declined");
        verify(paymentOutboxService, times(1)).enqueueDepositResult(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void bookingEventInitiation_shouldCreateFiftyPercentDeposit() {
        UUID bookingId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        PaymentTransactionResponse payment = paymentService.initiateDepositForBookingEvent(
            bookingId,
            customerId,
            new BigDecimal("400000"),
            "event-1"
        );

        assertThat(payment.amount()).isEqualByComparingTo("200000.00");
        List<PaymentTransactionResponse> byBooking = paymentService.listByBooking(bookingId);
        assertThat(byBooking).hasSize(1);
    }

    @Test
    void callbackSecret_shouldRejectWrongSecret() {
        assertThatThrownBy(() -> paymentService.validateCallbackSecret("wrong-secret"))
            .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);

        paymentService.validateCallbackSecret("test-callback-secret");
    }
}
