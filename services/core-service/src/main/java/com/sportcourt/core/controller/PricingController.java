package com.sportcourt.core.controller;

import com.sportcourt.core.api.ApiResponse;
import com.sportcourt.core.domain.enums.CustomerTier;
import com.sportcourt.core.dto.PricingQuoteResponse;
import com.sportcourt.core.dto.PricingRuleCreateRequest;
import com.sportcourt.core.dto.PricingRuleResponse;
import com.sportcourt.core.service.PricingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/core")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @PostMapping("/pricing-rules")
    public ResponseEntity<ApiResponse<PricingRuleResponse>> createPricingRule(
        @Valid @RequestBody PricingRuleCreateRequest req
    ) {
        PricingRuleResponse response = pricingService.createRule(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/pricing-rules")
    public ApiResponse<List<PricingRuleResponse>> listPricingRules(
        @RequestParam(required = false) UUID courtId
    ) {
        return ApiResponse.success(pricingService.listRules(courtId));
    }

    @GetMapping("/pricing/quote")
    public ApiResponse<PricingQuoteResponse> quote(
        @RequestParam UUID courtId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime start,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime end,
        @RequestParam(defaultValue = "STANDARD") CustomerTier customerTier
    ) {
        PricingQuoteResponse response = pricingService.quote(courtId, start, end, customerTier);
        return ApiResponse.success(response);
    }
}
