package com.sportcourt.payment.vnpay;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VNPayUtil {

    private static final String SECURE_HASH = "vnp_SecureHash";
    private static final String SECURE_HASH_TYPE = "vnp_SecureHashType";

    private VNPayUtil() {
    }

    public static String buildQueryString(Map<String, String> params) {
        return sortedUnsignedParams(params).entrySet().stream()
            .map(entry -> entry.getKey() + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    public static String buildHashData(Map<String, String> params) {
        return buildQueryString(params);
    }

    public static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKey);
            return toLowerHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate VNPAY signature", ex);
        }
    }

    public static boolean verifySignature(Map<String, String> params, String secret) {
        String providedHash = params.get(SECURE_HASH);
        if (providedHash == null || providedHash.isBlank()) {
            return false;
        }
        String expectedHash = hmacSha512(secret, buildHashData(params));
        return MessageDigest.isEqual(
            expectedHash.getBytes(StandardCharsets.UTF_8),
            providedHash.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
        );
    }

    public static Map<String, String> sortedUnsignedParams(Map<String, String> params) {
        return params.entrySet().stream()
            .filter(entry -> entry.getValue() != null && !entry.getValue().isBlank())
            .filter(entry -> !SECURE_HASH.equals(entry.getKey()) && !SECURE_HASH_TYPE.equals(entry.getKey()))
            .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    public static String normalizeOrderInfo(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return "Thanh toan dat san";
        }
        String normalized = Normalizer.normalize(rawValue, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .replaceAll("[^A-Za-z0-9 ]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        return normalized.isBlank() ? "Thanh toan dat san" : normalized;
    }

    public static String stableCallbackData(Map<String, String> params) {
        return sortedUnsignedParams(params).entrySet().stream()
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .collect(Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private static String toLowerHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }
}
