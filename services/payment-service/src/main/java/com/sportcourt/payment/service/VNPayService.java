package com.sportcourt.payment.service;

import com.sportcourt.payment.config.VNPayProperties;
import com.sportcourt.payment.domain.PaymentTransaction;
import com.sportcourt.payment.domain.enums.PaymentProvider;
import com.sportcourt.payment.domain.enums.PaymentTransactionStatus;
import com.sportcourt.payment.domain.enums.PaymentType;
import com.sportcourt.payment.dto.CreateVnpayPaymentRequest;
import com.sportcourt.payment.dto.CreateVnpayPaymentResponse;
import com.sportcourt.payment.dto.PaymentStatusByRefResponse;
import com.sportcourt.payment.dto.VnpayIpnResponse;
import com.sportcourt.payment.event.PaymentOutboxService;
import com.sportcourt.payment.repository.PaymentTransactionRepository;
import com.sportcourt.payment.vnpay.VNPayUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class VNPayService {

    private static final Logger log = LoggerFactory.getLogger(VNPayService.class);
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final UUID UNKNOWN_CUSTOMER_ID = new UUID(0L, 0L);

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentOutboxService paymentOutboxService;
    private final VNPayProperties properties;

    public VNPayService(PaymentTransactionRepository paymentTransactionRepository,
                        PaymentOutboxService paymentOutboxService,
                        VNPayProperties properties) {
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentOutboxService = paymentOutboxService;
        this.properties = properties;
    }

    @Transactional
    public CreateVnpayPaymentResponse createPayment(CreateVnpayPaymentRequest request, String ipAddress) {
        validateVnpayReady();

        String providedIdempotencyKey = normalizeNullable(request.idempotencyKey());
        if (providedIdempotencyKey != null) {
            var existing = paymentTransactionRepository.findByIdempotencyKey(providedIdempotencyKey);
            if (existing.isPresent()) {
                PaymentTransaction payment = existing.get();
                return new CreateVnpayPaymentResponse(payment.getId(), payment.getPaymentRef(), payment.getCheckoutUrl());
            }
        }

        String paymentRef = buildPaymentRef();
        String idempotencyKey = providedIdempotencyKey == null ? paymentRef : providedIdempotencyKey;
        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);
        ZonedDateTime expireAt = now.plusMinutes(properties.getExpireMinutes());

        PaymentTransaction payment = new PaymentTransaction();
        payment.setPaymentRef(paymentRef);
        payment.setBookingId(request.bookingId());
        payment.setCustomerId(request.customerId() == null ? UNKNOWN_CUSTOMER_ID : request.customerId());
        payment.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        payment.setCurrency(properties.getCurrCode());
        payment.setType(PaymentType.DEPOSIT);
        payment.setStatus(PaymentTransactionStatus.PENDING);
        payment.setIdempotencyKey(idempotencyKey);
        payment.setProvider(PaymentProvider.VNPAY);
        payment.setRequestedAt(OffsetDateTime.now(ZoneOffset.UTC));
        payment.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        Map<String, String> params = buildPaymentParams(request, paymentRef, ipAddress, now, expireAt);
        String hashData = VNPayUtil.buildHashData(params);
        String secureHash = VNPayUtil.hmacSha512(properties.getHashSecret(), hashData);
        String paymentUrl = properties.getPayUrl() + "?" + VNPayUtil.buildQueryString(params) + "&vnp_SecureHash=" + secureHash;
        payment.setCheckoutUrl(paymentUrl);

        PaymentTransaction saved = paymentTransactionRepository.save(payment);
        log.info("Created VNPAY payment paymentRef={} bookingId={}", paymentRef, request.bookingId());
        return new CreateVnpayPaymentResponse(saved.getId(), paymentRef, paymentUrl);
    }

    @Transactional
    public VnpayIpnResponse handleIpn(Map<String, String> params) {
        String paymentRef = params.get("vnp_TxnRef");
        log.info("Received VNPAY IPN paymentRef={}", paymentRef);

        CallbackProcessResult result = processCallback(params, "IPN");
        if (result == CallbackProcessResult.INVALID_REQUEST) {
            return new VnpayIpnResponse("99", "Invalid request");
        }
        if (result == CallbackProcessResult.INVALID_SIGNATURE) {
            return new VnpayIpnResponse("97", "Invalid Signature");
        }
        if (result == CallbackProcessResult.ORDER_NOT_FOUND) {
            return new VnpayIpnResponse("01", "Order not found");
        }
        if (result == CallbackProcessResult.INVALID_AMOUNT) {
            return new VnpayIpnResponse("04", "Invalid amount");
        }
        if (result == CallbackProcessResult.ALREADY_CONFIRMED) {
            return new VnpayIpnResponse("02", "Order Already Update");
        }
        return new VnpayIpnResponse("00", "Confirm Success");
    }

    @Transactional
    public String buildReturnRedirect(Map<String, String> params) {
        String paymentRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        boolean signatureValid = properties.getHashSecret() != null
            && !properties.getHashSecret().isBlank()
            && VNPayUtil.verifySignature(params, properties.getHashSecret());
        if (!signatureValid) {
            log.warn("Invalid VNPAY return signature paymentRef={}", paymentRef);
        }
        return UriComponentsBuilder.fromUriString(properties.getFrontendReturnUrl())
            .queryParam("paymentRef", paymentRef)
            .queryParam("responseCode", responseCode)
            .queryParam("signature", signatureValid ? "valid" : "unknown")
            .build()
            .toUriString();
    }

    @Transactional(readOnly = true)
    public PaymentStatusByRefResponse getByPaymentRef(String paymentRef) {
        PaymentTransaction payment = paymentTransactionRepository.findByPaymentRef(paymentRef)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment transaction not found"));
        return new PaymentStatusByRefResponse(
            payment.getPaymentRef(),
            payment.getBookingId(),
            payment.getAmount(),
            payment.getProvider(),
            payment.getStatus(),
            payment.getResponseCode(),
            payment.getTransactionStatus()
        );
    }

    private Map<String, String> buildPaymentParams(CreateVnpayPaymentRequest request,
                                                   String paymentRef,
                                                   String ipAddress,
                                                   ZonedDateTime createAt,
                                                   ZonedDateTime expireAt) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_Version", properties.getVersion());
        params.put("vnp_Command", properties.getCommand());
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", request.amount().multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).toPlainString());
        params.put("vnp_CurrCode", properties.getCurrCode());
        params.put("vnp_TxnRef", paymentRef);
        params.put("vnp_OrderInfo", VNPayUtil.normalizeOrderInfo(defaultOrderInfo(request)));
        params.put("vnp_OrderType", properties.getOrderType());
        params.put("vnp_Locale", properties.getLocale());
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", ipAddress);
        params.put("vnp_CreateDate", createAt.format(VNPAY_DATE_FORMAT));
        params.put("vnp_ExpireDate", expireAt.format(VNPAY_DATE_FORMAT));
        String bankCode = normalizeNullable(request.bankCode());
        if (bankCode != null) {
            params.put("vnp_BankCode", bankCode.toUpperCase(Locale.ROOT));
        }
        return params;
    }

    private String defaultOrderInfo(CreateVnpayPaymentRequest request) {
        if (request.orderInfo() != null && !request.orderInfo().isBlank()) {
            return request.orderInfo();
        }
        return "Thanh toan dat san " + request.bookingId();
    }

    private void validateVnpayReady() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VNPAY is disabled");
        }
        if (properties.getHashSecret() == null || properties.getHashSecret().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "VNPAY_HASH_SECRET is not configured");
        }
    }

    private boolean isSignatureValid(Map<String, String> params) {
        return properties.getHashSecret() != null
            && !properties.getHashSecret().isBlank()
            && VNPayUtil.verifySignature(params, properties.getHashSecret());
    }

    private CallbackProcessResult processCallback(Map<String, String> params, String source) {
        String paymentRef = params.get("vnp_TxnRef");
        if (isMissingRequiredCallbackParam(params)) {
            log.warn("Invalid VNPAY {} request missing required params paymentRef={}", source, paymentRef);
            return CallbackProcessResult.INVALID_REQUEST;
        }
        if (!properties.getTmnCode().equals(params.get("vnp_TmnCode"))) {
            log.warn("Invalid VNPAY {} tmnCode paymentRef={} tmnCode={}", source, paymentRef, params.get("vnp_TmnCode"));
            return CallbackProcessResult.INVALID_REQUEST;
        }
        if (!isSignatureValid(params)) {
            log.warn("Invalid VNPAY {} signature paymentRef={}", source, paymentRef);
            return CallbackProcessResult.INVALID_SIGNATURE;
        }

        PaymentTransaction payment = paymentTransactionRepository.findByPaymentRef(paymentRef)
            .orElse(null);
        if (payment == null) {
            log.warn("VNPAY {} order not found paymentRef={}", source, paymentRef);
            return CallbackProcessResult.ORDER_NOT_FOUND;
        }

        if (!isAmountValid(payment, params.get("vnp_Amount"))) {
            log.warn("VNPAY {} invalid amount paymentRef={}", source, paymentRef);
            return CallbackProcessResult.INVALID_AMOUNT;
        }

        if (payment.getStatus() != PaymentTransactionStatus.PENDING) {
            log.info("VNPAY {} payment already confirmed paymentRef={} status={}", source, paymentRef, payment.getStatus());
            return CallbackProcessResult.ALREADY_CONFIRMED;
        }

        String responseCode = params.get("vnp_ResponseCode");
        String transactionStatus = params.get("vnp_TransactionStatus");
        String providerTransactionNo = params.get("vnp_TransactionNo");
        payment.setProviderTransactionNo(providerTransactionNo);
        payment.setProviderReference(providerTransactionNo);
        payment.setBankCode(params.get("vnp_BankCode"));
        payment.setResponseCode(responseCode);
        payment.setTransactionStatus(transactionStatus);
        payment.setPayDate(params.get("vnp_PayDate"));
        payment.setRawCallbackData(VNPayUtil.stableCallbackData(params));
        payment.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        payment.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        if ("00".equals(responseCode) && "00".equals(transactionStatus)) {
            payment.setStatus(PaymentTransactionStatus.SUCCESS);
            payment.setFailureReason(null);
        } else {
            payment.setStatus(PaymentTransactionStatus.FAILED);
            payment.setFailureReason("VNPAY responseCode=" + responseCode + ", transactionStatus=" + transactionStatus);
        }

        PaymentTransaction saved = paymentTransactionRepository.save(payment);
        paymentOutboxService.enqueueDepositResult(saved);
        log.info("Updated VNPAY payment from {} paymentRef={} status={} responseCode={} transactionStatus={}",
            source, paymentRef, saved.getStatus(), responseCode, transactionStatus);
        return CallbackProcessResult.PROCESSED;
    }

    private boolean isMissingRequiredCallbackParam(Map<String, String> params) {
        return isBlank(params.get("vnp_TxnRef"))
            || isBlank(params.get("vnp_Amount"))
            || isBlank(params.get("vnp_ResponseCode"))
            || isBlank(params.get("vnp_TransactionStatus"))
            || isBlank(params.get("vnp_TmnCode"))
            || isBlank(params.get("vnp_SecureHash"));
    }

    private boolean isAmountValid(PaymentTransaction payment, String rawAmount) {
        try {
            BigDecimal callbackAmount = new BigDecimal(rawAmount).divide(BigDecimal.valueOf(100), 2, RoundingMode.UNNECESSARY);
            return callbackAmount.compareTo(payment.getAmount()) == 0;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String buildPaymentRef() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        return "VNPAY" + ZonedDateTime.now(VIETNAM_ZONE).format(VNPAY_DATE_FORMAT) + suffix;
    }

    private String normalizeNullable(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return rawValue.trim();
    }

    private boolean isBlank(String rawValue) {
        return rawValue == null || rawValue.isBlank();
    }

    private enum CallbackProcessResult {
        PROCESSED,
        INVALID_REQUEST,
        INVALID_SIGNATURE,
        ORDER_NOT_FOUND,
        INVALID_AMOUNT,
        ALREADY_CONFIRMED
    }
}
