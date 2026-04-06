package com.sportcourt.payment.controller;

import com.sportcourt.payment.dlq.DeadLetterEventService;
import com.sportcourt.payment.dlq.DeadLetterEventStatus;
import com.sportcourt.payment.dto.DeadLetterEventResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments/ops/dlq")
public class DlqOpsController {

    private final DeadLetterEventService deadLetterEventService;

    public DlqOpsController(DeadLetterEventService deadLetterEventService) {
        this.deadLetterEventService = deadLetterEventService;
    }

    @GetMapping
    public Page<DeadLetterEventResponse> list(
        @RequestParam(required = false) DeadLetterEventStatus status,
        @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return deadLetterEventService.list(status, pageable);
    }

    @PostMapping("/{id}/replay")
    public DeadLetterEventResponse replay(@PathVariable UUID id) {
        return deadLetterEventService.replay(id);
    }
}
