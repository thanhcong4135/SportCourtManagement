package com.sportcourt.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sportcourt.core.api.ApiErrorDetail;
import com.sportcourt.core.domain.Court;
import com.sportcourt.core.domain.PricingRule;
import com.sportcourt.core.domain.enums.CustomerTier;
import com.sportcourt.core.domain.enums.PricingDayType;
import com.sportcourt.core.domain.enums.PricingRuleCustomerTier;
import com.sportcourt.core.dto.PricingQuoteResponse;
import com.sportcourt.core.dto.PricingQuoteSlotResponse;
import com.sportcourt.core.dto.PricingRuleCreateRequest;
import com.sportcourt.core.dto.PricingRuleResponse;
import com.sportcourt.core.exception.BusinessException;
import com.sportcourt.core.repository.CourtRepository;
import com.sportcourt.core.repository.PricingRuleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class PricingService {

    private static final int SLOT_MINUTES = 30;

    private final PricingRuleRepository pricingRuleRepository;
    private final CourtRepository courtRepository;
    private final ObjectMapper objectMapper;
    private final ZoneId businessZoneId;
    private final ZoneId responseZoneId;
    private final BigDecimal defaultHourlyRate;

    public PricingService(PricingRuleRepository pricingRuleRepository,
                          CourtRepository courtRepository,
                          ObjectMapper objectMapper,
                          @Value("${app.time.business-zone:Asia/Ho_Chi_Minh}") String businessZone,
                          @Value("${app.time.response-zone:Asia/Ho_Chi_Minh}") String responseZone,
                          @Value("${app.pricing.default-hourly-rate:0}") BigDecimal defaultHourlyRate) {
        this.pricingRuleRepository = pricingRuleRepository;
        this.courtRepository = courtRepository;
        this.objectMapper = objectMapper;
        this.businessZoneId = ZoneId.of(businessZone);
        this.responseZoneId = ZoneId.of(responseZone);
        this.defaultHourlyRate = defaultHourlyRate == null ? BigDecimal.ZERO : defaultHourlyRate;
    }

    @Transactional
    public PricingRuleResponse createRule(PricingRuleCreateRequest req) {
        validateRuleWindow(req.startTime(), req.endTime());
        Court court = courtRepository.findById(req.courtId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));
        int requestedPriority = req.priority() == null ? 0 : req.priority();
        validateRuleOverlap(court.getId(), req.dayType(), req.customerTier(), req.startTime(), req.endTime(), requestedPriority);

        PricingRule rule = new PricingRule();
        rule.setCourt(court);
        rule.setName(req.name().trim());
        rule.setDayType(req.dayType());
        rule.setStartTime(req.startTime());
        rule.setEndTime(req.endTime());
        rule.setCustomerTier(req.customerTier());
        rule.setPricePerHour(req.pricePerHour().setScale(2, RoundingMode.HALF_UP));
        rule.setPriority(requestedPriority);
        rule.setActive(true);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        rule.setCreatedAt(now);
        rule.setUpdatedAt(now);

        PricingRule saved = pricingRuleRepository.save(rule);
        return toRuleResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PricingRuleResponse> listRules(UUID courtId) {
        List<PricingRule> rules = (courtId == null)
            ? pricingRuleRepository.findAll().stream()
            .sorted(Comparator.comparing((PricingRule r) -> r.getCourt().getId().toString())
                .thenComparing(PricingRule::getPriority, Comparator.reverseOrder()))
            .toList()
            : pricingRuleRepository.findByCourtIdOrderByPriorityDescCreatedAtAsc(courtId);
        return rules.stream().map(this::toRuleResponse).toList();
    }

    @Transactional(readOnly = true)
    public PricingQuoteResponse quote(UUID courtId,
                                      OffsetDateTime startTime,
                                      OffsetDateTime endTime,
                                      CustomerTier customerTier) {
        PricingQuoteResult result = quoteInternal(courtId, startTime, endTime, customerTier, null);
        return toQuoteResponse(courtId, startTime, endTime, result);
    }

    @Transactional(readOnly = true)
    public PricingQuoteResult quoteForBooking(UUID courtId,
                                              OffsetDateTime startTime,
                                              OffsetDateTime endTime,
                                              CustomerTier customerTier,
                                              BigDecimal fallbackPriceTotal) {
        return quoteInternal(courtId, startTime, endTime, customerTier, fallbackPriceTotal);
    }

    private PricingQuoteResult quoteInternal(UUID courtId,
                                             OffsetDateTime startTime,
                                             OffsetDateTime endTime,
                                             CustomerTier customerTier,
                                             BigDecimal fallbackPriceTotal) {
        validateTimeRange(startTime, endTime);
        validateSlotAlignment(startTime, endTime);
        courtRepository.findById(courtId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Court not found"));

        OffsetDateTime normalizedStart = normalizeToUtc(startTime);
        OffsetDateTime normalizedEnd = normalizeToUtc(endTime);
        CustomerTier effectiveTier = (customerTier == null) ? CustomerTier.STANDARD : customerTier;

        List<PricingRule> rules = pricingRuleRepository.findActiveCandidates(
            courtId,
            List.of(PricingRuleCustomerTier.ALL, mapTier(effectiveTier)),
            List.of(PricingDayType.ALL, PricingDayType.WEEKDAY, PricingDayType.WEEKEND)
        );

        List<PricingQuoteSlot> slots = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        OffsetDateTime cursor = normalizedStart;
        while (cursor.isBefore(normalizedEnd)) {
            OffsetDateTime slotEnd = cursor.plusMinutes(SLOT_MINUTES);
            PricingRule matched = findRuleForSlot(rules, cursor);
            if (matched == null) {
                if (fallbackPriceTotal != null && fallbackPriceTotal.compareTo(BigDecimal.ZERO) > 0) {
                    return quoteWithFallback(
                        normalizedStart,
                        normalizedEnd,
                        effectiveTier,
                        fallbackPriceTotal,
                        cursor
                    );
                }

                PricingQuoteSlot fallbackSlot = buildDefaultFallbackSlot(cursor, slotEnd);
                if (fallbackSlot == null) {
                    throw new BusinessException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "PRICING_RULE_MISSING",
                        "No pricing rule for slot starting " + toResponseOffset(cursor),
                        List.of(new ApiErrorDetail("missingSlotStart", toResponseOffset(cursor).toString()))
                    );
                }
                slots.add(fallbackSlot);
                total = total.add(fallbackSlot.amount());
                cursor = slotEnd;
                continue;
            }

            BigDecimal amount = hourlyToSlotAmount(matched.getPricePerHour());
            slots.add(new PricingQuoteSlot(
                toResponseOffset(cursor),
                toResponseOffset(slotEnd),
                matched.getId(),
                matched.getName(),
                matched.getPricePerHour(),
                amount
            ));
            total = total.add(amount);
            cursor = slotEnd;
        }

        return new PricingQuoteResult(
            effectiveTier,
            total.setScale(2, RoundingMode.HALF_UP),
            slots,
            buildSnapshotJson(effectiveTier, normalizedStart, normalizedEnd, slots, total)
        );
    }

    private PricingQuoteResult quoteWithFallback(OffsetDateTime normalizedStart,
                                                 OffsetDateTime normalizedEnd,
                                                 CustomerTier customerTier,
                                                 BigDecimal fallbackPriceTotal,
                                                 OffsetDateTime missingSlotStart) {
        if (fallbackPriceTotal == null || fallbackPriceTotal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "PRICING_RULE_MISSING",
                "No pricing rule for slot starting " + toResponseOffset(missingSlotStart),
                List.of(new ApiErrorDetail("missingSlotStart", toResponseOffset(missingSlotStart).toString()))
            );
        }

        BigDecimal total = fallbackPriceTotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal hours = BigDecimal.valueOf(Duration.between(normalizedStart, normalizedEnd).toMinutes())
            .divide(BigDecimal.valueOf(60), 4, RoundingMode.HALF_UP);
        BigDecimal hourly = total.divide(hours, 2, RoundingMode.HALF_UP);

        List<PricingQuoteSlot> slots = List.of(
            new PricingQuoteSlot(
                toResponseOffset(normalizedStart),
                toResponseOffset(normalizedEnd),
                null,
                "MANUAL_PRICE_INPUT",
                hourly,
                total
            )
        );

        return new PricingQuoteResult(
            customerTier,
            total,
            slots,
            buildSnapshotJson(customerTier, normalizedStart, normalizedEnd, slots, total)
        );
    }

    private PricingQuoteSlot buildDefaultFallbackSlot(OffsetDateTime slotStart, OffsetDateTime slotEnd) {
        if (defaultHourlyRate.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        BigDecimal normalizedRate = defaultHourlyRate.setScale(2, RoundingMode.HALF_UP);
        return new PricingQuoteSlot(
            toResponseOffset(slotStart),
            toResponseOffset(slotEnd),
            null,
            "DEFAULT_FALLBACK_RATE",
            normalizedRate,
            hourlyToSlotAmount(normalizedRate)
        );
    }

    private PricingRule findRuleForSlot(List<PricingRule> rules, OffsetDateTime slotStartUtc) {
        OffsetDateTime local = slotStartUtc.atZoneSameInstant(businessZoneId).toOffsetDateTime();
        LocalTime localTime = local.toLocalTime();
        PricingDayType dayType = mapDayType(local.getDayOfWeek());

        return rules.stream()
            .filter(rule -> (rule.getDayType() == PricingDayType.ALL || rule.getDayType() == dayType)
                && !localTime.isBefore(rule.getStartTime())
                && localTime.isBefore(rule.getEndTime()))
            .findFirst()
            .orElse(null);
    }

    private PricingQuoteResponse toQuoteResponse(UUID courtId,
                                                 OffsetDateTime startTime,
                                                 OffsetDateTime endTime,
                                                 PricingQuoteResult result) {
        List<PricingQuoteSlotResponse> slots = result.slots().stream()
            .map(slot -> new PricingQuoteSlotResponse(
                slot.startTime(),
                slot.endTime(),
                slot.ruleId(),
                slot.ruleName(),
                slot.pricePerHour(),
                slot.amount()
            ))
            .toList();
        return new PricingQuoteResponse(
            courtId,
            result.customerTier(),
            toResponseOffset(normalizeToUtc(startTime)),
            toResponseOffset(normalizeToUtc(endTime)),
            result.totalPrice(),
            slots
        );
    }

    private String buildSnapshotJson(CustomerTier customerTier,
                                     OffsetDateTime startTime,
                                     OffsetDateTime endTime,
                                     List<PricingQuoteSlot> slots,
                                     BigDecimal total) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("customerTier", customerTier.name());
        root.put("quotedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        root.put("startTime", startTime.toString());
        root.put("endTime", endTime.toString());
        root.put("totalPrice", total.setScale(2, RoundingMode.HALF_UP).toPlainString());

        ArrayNode slotArray = root.putArray("slots");
        for (PricingQuoteSlot slot : slots) {
            ObjectNode node = slotArray.addObject();
            node.put("startTime", slot.startTime().toString());
            node.put("endTime", slot.endTime().toString());
            if (slot.ruleId() != null) {
                node.put("ruleId", slot.ruleId().toString());
            }
            node.put("ruleName", slot.ruleName());
            node.put("pricePerHour", slot.pricePerHour().setScale(2, RoundingMode.HALF_UP).toPlainString());
            node.put("amount", slot.amount().setScale(2, RoundingMode.HALF_UP).toPlainString());
        }

        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Cannot serialize pricing snapshot");
        }
    }

    private void validateRuleWindow(LocalTime startTime, LocalTime endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pricing window");
        }
        if (startTime.getMinute() % SLOT_MINUTES != 0 || endTime.getMinute() % SLOT_MINUTES != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pricing window must align to 30-minute slots");
        }
    }

    private void validateRuleOverlap(UUID courtId,
                                     PricingDayType candidateDayType,
                                     PricingRuleCustomerTier candidateTier,
                                     LocalTime candidateStart,
                                     LocalTime candidateEnd,
                                     int candidatePriority) {
        List<PricingRule> existingRules = pricingRuleRepository.findByCourtIdOrderByPriorityDescCreatedAtAsc(courtId);
        int candidateSpecificity = specificity(candidateDayType, candidateTier);

        for (PricingRule existing : existingRules) {
            if (!existing.isActive()) {
                continue;
            }
            if (!isTimeOverlapped(candidateStart, candidateEnd, existing.getStartTime(), existing.getEndTime())) {
                continue;
            }
            if (!isDayScopeOverlapped(candidateDayType, existing.getDayType())) {
                continue;
            }
            if (!isTierScopeOverlapped(candidateTier, existing.getCustomerTier())) {
                continue;
            }

            int existingSpecificity = specificity(existing.getDayType(), existing.getCustomerTier());
            if (candidateSpecificity == existingSpecificity) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Pricing rule overlaps existing rule with same scope: " + existing.getName()
                );
            }

            if (candidateSpecificity > existingSpecificity && candidatePriority <= existing.getPriority()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Specific pricing rule must have higher priority than broader overlapping rule: " + existing.getName()
                );
            }

            if (candidateSpecificity < existingSpecificity && candidatePriority >= existing.getPriority()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Fallback/broader pricing rule must have lower priority than specific overlapping rule: " + existing.getName()
                );
            }
        }
    }

    private void validateTimeRange(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime == null || endTime == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start/end time required");
        }
        if (!endTime.isAfter(startTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid time range");
        }
    }

    private void validateSlotAlignment(OffsetDateTime startTime, OffsetDateTime endTime) {
        if (startTime.getMinute() % SLOT_MINUTES != 0 || endTime.getMinute() % SLOT_MINUTES != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Time must align to 30-minute slots");
        }
        long minutes = Duration.between(startTime, endTime).toMinutes();
        if (minutes % SLOT_MINUTES != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Duration must be a multiple of 30 minutes");
        }
    }

    private PricingRuleResponse toRuleResponse(PricingRule rule) {
        return new PricingRuleResponse(
            rule.getId(),
            rule.getCourt().getId(),
            rule.getName(),
            rule.getDayType(),
            rule.getStartTime(),
            rule.getEndTime(),
            rule.getCustomerTier(),
            rule.getPricePerHour(),
            rule.getPriority(),
            rule.isActive(),
            rule.getCreatedAt(),
            rule.getUpdatedAt()
        );
    }

    private PricingRuleCustomerTier mapTier(CustomerTier customerTier) {
        return switch (customerTier) {
            case MEMBER -> PricingRuleCustomerTier.MEMBER;
            case VIP -> PricingRuleCustomerTier.VIP;
            case STANDARD -> PricingRuleCustomerTier.STANDARD;
        };
    }

    private boolean isTimeOverlapped(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    private boolean isDayScopeOverlapped(PricingDayType left, PricingDayType right) {
        return left == PricingDayType.ALL || right == PricingDayType.ALL || left == right;
    }

    private boolean isTierScopeOverlapped(PricingRuleCustomerTier left, PricingRuleCustomerTier right) {
        return left == PricingRuleCustomerTier.ALL || right == PricingRuleCustomerTier.ALL || left == right;
    }

    private int specificity(PricingDayType dayType, PricingRuleCustomerTier tier) {
        int dayScore = dayType == PricingDayType.ALL ? 0 : 1;
        int tierScore = tier == PricingRuleCustomerTier.ALL ? 0 : 1;
        return dayScore + tierScore;
    }

    private PricingDayType mapDayType(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case SATURDAY, SUNDAY -> PricingDayType.WEEKEND;
            default -> PricingDayType.WEEKDAY;
        };
    }

    private BigDecimal hourlyToSlotAmount(BigDecimal pricePerHour) {
        return pricePerHour
            .multiply(BigDecimal.valueOf(SLOT_MINUTES))
            .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }

    private OffsetDateTime normalizeToUtc(OffsetDateTime value) {
        return value.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private OffsetDateTime toResponseOffset(OffsetDateTime value) {
        return value.atZoneSameInstant(responseZoneId).toOffsetDateTime();
    }
}
