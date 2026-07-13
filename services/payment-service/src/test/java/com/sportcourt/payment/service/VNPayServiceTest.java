package com.sportcourt.payment.service;

import com.sportcourt.payment.config.VNPayProperties;
import com.sportcourt.payment.domain.PaymentTransaction;
import com.sportcourt.payment.domain.enums.PaymentProvider;
import com.sportcourt.payment.domain.enums.PaymentTransactionStatus;
import com.sportcourt.payment.domain.enums.PaymentType;
import com.sportcourt.payment.dto.VnpayIpnResponse;
import com.sportcourt.payment.event.PaymentOutboxService;
import com.sportcourt.payment.repository.PaymentTransactionRepository;
import com.sportcourt.payment.vnpay.VNPayUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VNPayServiceTest {

    private static final String HASH_SECRET = "test-secret";
    private static final String TMN_CODE = "7FXP85ML";

    @Mock
    private PaymentTransactionRepository paymentTransactionRepository;

    @Mock
    private PaymentOutboxService paymentOutboxService;

    private VNPayService vnPayService;

    @BeforeEach
    void setUp() {
        VNPayProperties properties = new VNPayProperties();
        properties.setEnabled(true);
        properties.setTmnCode(TMN_CODE);
        properties.setHashSecret(HASH_SECRET);
        properties.setFrontendReturnUrl("http://localhost:5173/payment-result");
        vnPayService = new VNPayService(paymentTransactionRepository, paymentOutboxService, properties);
    }

    @Test
    void handleIpn_shouldUpdatePendingPaymentAndReturnConfirmSuccess() {
        PaymentTransaction payment = pendingPayment("VNPAY123", new BigDecimal("150000.00"));
        when(paymentTransactionRepository.findByPaymentRef("VNPAY123")).thenReturn(Optional.of(payment));
        when(paymentTransactionRepository.save(any(PaymentTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VnpayIpnResponse response = vnPayService.handleIpn(signedCallback("VNPAY123", "15000000"));

        assertThat(response.rspCode()).isEqualTo("00");
        assertThat(response.message()).isEqualTo("Confirm Success");
        assertThat(payment.getStatus()).isEqualTo(PaymentTransactionStatus.SUCCESS);
        assertThat(payment.getResponseCode()).isEqualTo("00");
        assertThat(payment.getTransactionStatus()).isEqualTo("00");
        verify(paymentOutboxService).enqueueDepositResult(payment);
    }

    @Test
    void handleIpn_shouldReturnAlreadyUpdatedForNonPendingPayment() {
        PaymentTransaction payment = pendingPayment("VNPAY123", new BigDecimal("150000.00"));
        payment.setStatus(PaymentTransactionStatus.SUCCESS);
        when(paymentTransactionRepository.findByPaymentRef("VNPAY123")).thenReturn(Optional.of(payment));

        VnpayIpnResponse response = vnPayService.handleIpn(signedCallback("VNPAY123", "15000000"));

        assertThat(response.rspCode()).isEqualTo("02");
        assertThat(response.message()).isEqualTo("Order Already Update");
        verify(paymentTransactionRepository, never()).save(any());
        verifyNoInteractions(paymentOutboxService);
    }

    @Test
    void buildReturnRedirect_shouldNotUpdatePaymentState() {
        String redirect = vnPayService.buildReturnRedirect(signedCallback("VNPAY123", "15000000"));

        assertThat(redirect).isEqualTo("http://localhost:5173/payment-result?paymentRef=VNPAY123&responseCode=00&signature=valid");
        verifyNoInteractions(paymentTransactionRepository, paymentOutboxService);
    }

    @Test
    void handleIpn_shouldReturnInvalidRequestWhenRequiredParamIsMissing() {
        Map<String, String> params = signedCallback("VNPAY123", "15000000");
        params.remove("vnp_TransactionStatus");

        VnpayIpnResponse response = vnPayService.handleIpn(params);

        assertThat(response.rspCode()).isEqualTo("99");
        assertThat(response.message()).isEqualTo("Invalid request");
        verifyNoInteractions(paymentTransactionRepository, paymentOutboxService);
    }

    private PaymentTransaction pendingPayment(String paymentRef, BigDecimal amount) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setPaymentRef(paymentRef);
        payment.setBookingId(UUID.randomUUID());
        payment.setCustomerId(UUID.randomUUID());
        payment.setAmount(amount);
        payment.setCurrency("VND");
        payment.setType(PaymentType.DEPOSIT);
        payment.setStatus(PaymentTransactionStatus.PENDING);
        payment.setIdempotencyKey(paymentRef);
        payment.setProvider(PaymentProvider.VNPAY);
        payment.setRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
        return payment;
    }

    private Map<String, String> signedCallback(String paymentRef, String amount) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Amount", amount);
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_PayDate", "20260528153911");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_TransactionNo", "15559834");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TxnRef", paymentRef);
        params.put("vnp_SecureHash", VNPayUtil.hmacSha512(HASH_SECRET, VNPayUtil.buildHashData(params)));
        return params;
    }
}
