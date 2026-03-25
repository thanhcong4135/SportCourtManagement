package com.sportcourt.payment.service;

import com.sportcourt.payment.dto.PaymentCallbackRequest;
import com.sportcourt.payment.event.PaymentOutboxService;
import com.sportcourt.payment.provider.PaymentProviderClientResolver;
import com.sportcourt.payment.repository.PaymentTransactionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PaymentServiceSignatureValidationTest {

    @Test
    void validateCallbackSignature_shouldPassWhenSignatureValid() {
        PaymentService paymentService = newPaymentService(true, "sign-secret", 300);
        PaymentCallbackRequest request = new PaymentCallbackRequest(
            UUID.randomUUID(),
            "provider-ref",
            true,
            null
        );
        String timestamp = String.valueOf(OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());
        String signature = sign("sign-secret", payload(request, timestamp));

        assertThatCode(() -> paymentService.validateCallbackSignature(request, signature, timestamp))
            .doesNotThrowAnyException();
    }

    @Test
    void validateCallbackSignature_shouldRejectWhenSignatureInvalid() {
        PaymentService paymentService = newPaymentService(true, "sign-secret", 300);
        PaymentCallbackRequest request = new PaymentCallbackRequest(
            UUID.randomUUID(),
            "provider-ref",
            false,
            "declined"
        );
        String timestamp = String.valueOf(OffsetDateTime.now(ZoneOffset.UTC).toEpochSecond());

        assertThatThrownBy(() -> paymentService.validateCallbackSignature(request, "bad-signature", timestamp))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void validateCallbackSignature_shouldRejectWhenTimestampExpired() {
        PaymentService paymentService = newPaymentService(true, "sign-secret", 10);
        PaymentCallbackRequest request = new PaymentCallbackRequest(
            UUID.randomUUID(),
            "provider-ref",
            true,
            null
        );
        String timestamp = String.valueOf(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1).toEpochSecond());
        String signature = sign("sign-secret", payload(request, timestamp));

        assertThatThrownBy(() -> paymentService.validateCallbackSignature(request, signature, timestamp))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private PaymentService newPaymentService(boolean signatureEnabled, String signatureSecret, long maxSkewSeconds) {
        return new PaymentService(
            mock(PaymentTransactionRepository.class),
            mock(PaymentOutboxService.class),
            mock(PaymentProviderClientResolver.class),
            new BigDecimal("0.5"),
            "MOCK",
            "callback-secret",
            signatureEnabled,
            signatureSecret,
            maxSkewSeconds
        );
    }

    private String payload(PaymentCallbackRequest request, String timestamp) {
        String failureReason = request.failureReason() == null ? "" : request.failureReason();
        return request.paymentId() + "|" + request.providerReference() + "|" + request.success() + "|" + failureReason + "|" + timestamp;
    }

    private String sign(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format(Locale.ROOT, "%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
