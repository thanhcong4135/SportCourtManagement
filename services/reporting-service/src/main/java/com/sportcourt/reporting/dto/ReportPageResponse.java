package com.sportcourt.reporting.dto;

import java.util.List;

public record ReportPageResponse<T>(
    List<T> items,
    long totalItems,
    int page,
    int size,
    int totalPages
) {
}
