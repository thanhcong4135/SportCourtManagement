package com.sportcourt.payment.controller;

import com.sportcourt.payment.dto.CreateDepositPaymentRequest;
import com.sportcourt.payment.dto.PaymentCallbackRequest;
import com.sportcourt.payment.dto.PaymentTransactionResponse;
import com.sportcourt.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/deposits/initiate")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentTransactionResponse initiateDeposit(@Valid @RequestBody CreateDepositPaymentRequest request) {
        return paymentService.initiateDeposit(request);
    }

    @PostMapping("/callback")
    public PaymentTransactionResponse paymentCallback(
        @RequestHeader(name = "X-Payment-Callback-Secret", required = false) String callbackSecret,
        @RequestHeader(name = "X-Payment-Signature", required = false) String callbackSignature,
        @RequestHeader(name = "X-Payment-Timestamp", required = false) String callbackTimestamp,
        @Valid @RequestBody PaymentCallbackRequest request
    ) {
        paymentService.validateCallbackSecret(callbackSecret);
        paymentService.validateCallbackSignature(request, callbackSignature, callbackTimestamp);
        return paymentService.applyCallback(request);
    }

    @GetMapping("/{paymentId}")
    public PaymentTransactionResponse getById(@PathVariable UUID paymentId) {
        return paymentService.getById(paymentId);
    }

    @GetMapping("/booking/{bookingId}")
    public List<PaymentTransactionResponse> listByBooking(@PathVariable UUID bookingId) {
        return paymentService.listByBooking(bookingId);
    }
}
