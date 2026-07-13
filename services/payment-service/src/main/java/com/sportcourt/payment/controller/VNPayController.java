package com.sportcourt.payment.controller;

import com.sportcourt.payment.dto.CreateVnpayPaymentRequest;
import com.sportcourt.payment.dto.CreateVnpayPaymentResponse;
import com.sportcourt.payment.dto.PaymentStatusByRefResponse;
import com.sportcourt.payment.dto.VnpayIpnResponse;
import com.sportcourt.payment.service.VNPayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class VNPayController {

    private static final Logger log = LoggerFactory.getLogger(VNPayController.class);

    private final VNPayService vnPayService;

    public VNPayController(VNPayService vnPayService) {
        this.vnPayService = vnPayService;
    }

    @PostMapping("/vnpay/create-payment")
    @ResponseStatus(HttpStatus.CREATED)
    public CreateVnpayPaymentResponse createPayment(@Valid @RequestBody CreateVnpayPaymentRequest request,
                                                    HttpServletRequest servletRequest) {
        return vnPayService.createPayment(request, resolveClientIp(servletRequest));
    }

    @GetMapping("/vnpay/ipn")
    public VnpayIpnResponse ipn(@RequestParam Map<String, String> params) {
        try {
            return vnPayService.handleIpn(params);
        } catch (Exception ex) {
            log.error("Unexpected VNPAY IPN error paymentRef={}", params.get("vnp_TxnRef"), ex);
            return new VnpayIpnResponse("99", "Unknown error");
        }
    }

    @GetMapping("/vnpay/return")
    public ResponseEntity<Void> returnUrl(@RequestParam Map<String, String> params) {
        URI redirectUri = URI.create(vnPayService.buildReturnRedirect(params));
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, redirectUri.toString())
            .build();
    }

    @GetMapping("/by-ref/{paymentRef}")
    public PaymentStatusByRefResponse getByRef(@PathVariable String paymentRef) {
        return vnPayService.getByPaymentRef(paymentRef);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
