package com.sportcourt.payment.service;

import com.sportcourt.payment.domain.PaymentTransaction;
import com.sportcourt.payment.domain.enums.PaymentProvider;
import com.sportcourt.payment.domain.enums.PaymentTransactionStatus;
import com.sportcourt.payment.domain.enums.PaymentType;
import com.sportcourt.payment.dto.CreateDepositPaymentRequest;
import com.sportcourt.payment.dto.PaymentCallbackRequest;
import com.sportcourt.payment.dto.PaymentTransactionResponse;
import com.sportcourt.payment.event.PaymentOutboxService;
import com.sportcourt.payment.provider.PaymentProviderClientResolver;
import com.sportcourt.payment.provider.ProviderPaymentSession;
import com.sportcourt.payment.repository.PaymentTransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentOutboxService paymentOutboxService;
    private final PaymentProviderClientResolver paymentProviderClientResolver;
    private final BigDecimal depositRatio;
    private final PaymentProvider defaultProvider;
    private final String callbackSharedSecret;

    public PaymentService(PaymentTransactionRepository paymentTransactionRepository,
                          PaymentOutboxService paymentOutboxService,
                          PaymentProviderClientResolver paymentProviderClientResolver,
                          @Value("${payment.deposit.ratio:0.5}") BigDecimal depositRatio,
                          @Value("${payment.provider.type:MOCK}") String providerType,
                          @Value("${payment.callback.shared-secret:}") String callbackSharedSecret) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentOutboxService = paymentOutboxService;
        this.paymentProviderClientResolver = paymentProviderClientResolver;
        this.depositRatio = depositRatio;
        this.defaultProvider = PaymentProvider.fromConfig(providerType);
        this.callbackSharedSecret = callbackSharedSecret == null ? "" : callbackSharedSecret.trim();
    }

    public void validateCallbackSecret(String providedSecret) {
        if (callbackSharedSecret.isBlank()) {
            return;
        }
        if (providedSecret == null || !callbackSharedSecret.equals(providedSecret)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid payment callback secret");
        }
    }

    @Transactional
    public PaymentTransactionResponse initiateDeposit(CreateDepositPaymentRequest request) {
        return paymentTransactionRepository.findByIdempotencyKey(request.idempotencyKey())
            .map(this::toResponse)
            .orElseGet(() -> createDepositInternal(
                request.bookingId(),
                request.customerId(),
                request.amount(),
                request.currency(),
                request.idempotencyKey()
            ));
    }

    @Transactional
    public PaymentTransactionResponse initiateDepositForBookingEvent(UUID bookingId,
                                                                     UUID customerId,
                                                                     BigDecimal bookingPriceTotal,
                                                                     String eventId) {
        BigDecimal depositAmount = bookingPriceTotal
            .multiply(depositRatio)
            .setScale(2, RoundingMode.HALF_UP);
        return paymentTransactionRepository.findByIdempotencyKey(eventId)
            .map(this::toResponse)
            .orElseGet(() -> createDepositInternal(
                bookingId,
                customerId,
                depositAmount,
                "VND",
                eventId
            ));
    }

    @Transactional
    public PaymentTransactionResponse applyCallback(PaymentCallbackRequest request) {
        PaymentTransaction payment = paymentTransactionRepository.findById(request.paymentId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));

        if (payment.getStatus() != PaymentTransactionStatus.PENDING) {
            return toResponse(payment);
        }

        payment.setProviderReference(request.providerReference());
        payment.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));

        if (request.success()) {
            payment.setStatus(PaymentTransactionStatus.SUCCESS);
            payment.setFailureReason(null);
        } else {
            payment.setStatus(PaymentTransactionStatus.FAILED);
            payment.setFailureReason(trimFailureReason(request.failureReason()));
        }

        PaymentTransaction saved = paymentTransactionRepository.save(payment);
        paymentOutboxService.enqueueDepositResult(saved);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public PaymentTransactionResponse getById(UUID paymentId) {
        PaymentTransaction payment = paymentTransactionRepository.findById(paymentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));
        return toResponse(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentTransactionResponse> listByBooking(UUID bookingId) {
        return paymentTransactionRepository.findByBookingIdOrderByRequestedAtDesc(bookingId)
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private PaymentTransactionResponse createDepositInternal(UUID bookingId,
                                                             UUID customerId,
                                                             BigDecimal amount,
                                                             String currency,
                                                             String idempotencyKey) {
        PaymentTransaction payment = new PaymentTransaction();
        payment.setBookingId(bookingId);
        payment.setCustomerId(customerId);
        payment.setAmount(amount);
        payment.setCurrency(normalizeCurrency(currency));
        payment.setType(PaymentType.DEPOSIT);
        payment.setStatus(PaymentTransactionStatus.PENDING);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setProvider(defaultProvider);
        payment.setRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));

        PaymentTransaction saved = paymentTransactionRepository.save(payment);
        ProviderPaymentSession session = paymentProviderClientResolver.resolve(defaultProvider)
            .createDepositSession(saved);
        saved.setProviderReference(session.providerReference());
        saved.setCheckoutUrl(session.checkoutUrl());
        PaymentTransaction updated = paymentTransactionRepository.save(saved);
        return toResponse(updated);
    }

    private String normalizeCurrency(String currency) {
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private String trimFailureReason(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() <= 512 ? reason : reason.substring(0, 512);
    }

    private PaymentTransactionResponse toResponse(PaymentTransaction payment) {
        return new PaymentTransactionResponse(
            payment.getId(),
            payment.getBookingId(),
            payment.getCustomerId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getType(),
            payment.getStatus(),
            payment.getIdempotencyKey(),
            payment.getProvider(),
            payment.getProviderReference(),
            payment.getCheckoutUrl(),
            payment.getRequestedAt(),
            payment.getCompletedAt(),
            payment.getFailureReason()
        );
    }
}
