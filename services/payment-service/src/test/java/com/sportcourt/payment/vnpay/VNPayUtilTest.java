package com.sportcourt.payment.vnpay;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VNPayUtilTest {

    @Test
    void buildHashData_shouldUseVnpayFormEncoding() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_OrderInfo", "Thanh toan dat san");
        params.put("vnp_TmnCode", "7FXP85ML");

        String hashData = VNPayUtil.buildHashData(params);

        assertThat(hashData).isEqualTo("vnp_OrderInfo=Thanh+toan+dat+san&vnp_TmnCode=7FXP85ML");
    }

    @Test
    void verifySignature_shouldIgnoreSecureHashParams() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("vnp_OrderInfo", "Thanh toan dat san");
        params.put("vnp_TmnCode", "7FXP85ML");
        params.put("vnp_SecureHashType", "HmacSHA512");
        params.put("vnp_SecureHash", VNPayUtil.hmacSha512("secret", VNPayUtil.buildHashData(params)));

        assertThat(VNPayUtil.verifySignature(params, "secret")).isTrue();
    }
}
