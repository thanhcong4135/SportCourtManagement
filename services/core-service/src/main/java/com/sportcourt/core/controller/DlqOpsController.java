package com.sportcourt.core.controller;

import com.sportcourt.core.api.ApiResponse;
import com.sportcourt.core.api.PageResponse;
import com.sportcourt.core.dlq.DeadLetterEventService;
import com.sportcourt.core.dlq.DeadLetterEventStatus;
import com.sportcourt.core.dto.DeadLetterEventResponse;
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
@RequestMapping("/api/core/ops/dlq")
public class DlqOpsController {

    private final DeadLetterEventService deadLetterEventService;

    public DlqOpsController(DeadLetterEventService deadLetterEventService) {
        this.deadLetterEventService = deadLetterEventService;
    }

    @GetMapping
    public ApiResponse<PageResponse<DeadLetterEventResponse>> list(
        @RequestParam(required = false) DeadLetterEventStatus status,
        @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ApiResponse.success(PageResponse.from(deadLetterEventService.list(status, pageable)));
    }

    @PostMapping("/{id}/replay")
    public ApiResponse<DeadLetterEventResponse> replay(@PathVariable UUID id) {
        return ApiResponse.success(deadLetterEventService.replay(id));
    }
}
