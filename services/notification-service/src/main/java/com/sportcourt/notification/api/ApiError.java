package com.sportcourt.notification.api;

import java.time.OffsetDateTime;
import java.util.List;

public record ApiError(
    String code,
    String message,
    List<ApiErrorDetail> details,
    String traceId,
    OffsetDateTime timestamp,
    int status,
    String path,
    String error
) {
}
