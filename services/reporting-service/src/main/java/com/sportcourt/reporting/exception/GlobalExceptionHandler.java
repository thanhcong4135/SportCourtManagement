package com.sportcourt.reporting.exception;

import com.sportcourt.reporting.api.ApiError;
import com.sportcourt.reporting.api.ApiErrorDetail;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TRACE_HEADER = "X-Trace-Id";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiErrorDetail> details = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::toDetail)
            .toList();
        return ResponseEntity.badRequest()
            .body(buildError(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex,
                                                              HttpServletRequest request) {
        List<ApiErrorDetail> details = ex.getConstraintViolations()
            .stream()
            .map(v -> new ApiErrorDetail(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
        return ResponseEntity.badRequest()
            .body(buildError(request, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details));
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleMalformedRequest(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest()
            .body(buildError(request, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Malformed request payload", null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
        return ResponseEntity.status(status)
            .body(buildError(request, status, codeForStatus(status), message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnhandled(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception at {}", request.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(buildError(request, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected server error", null));
    }

    private ApiError buildError(HttpServletRequest request,
                                HttpStatus status,
                                String code,
                                String message,
                                List<ApiErrorDetail> details) {
        return new ApiError(
            code,
            message,
            details,
            resolveTraceId(request),
            OffsetDateTime.now(),
            status.value(),
            request.getRequestURI(),
            status.getReasonPhrase()
        );
    }

    private String codeForStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            default -> "BUSINESS_ERROR";
        };
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            Object traceAttribute = request.getAttribute(TRACE_HEADER);
            if (traceAttribute != null) {
                traceId = traceAttribute.toString();
            }
        }
        return (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
    }

    private ApiErrorDetail toDetail(FieldError error) {
        return new ApiErrorDetail(error.getField(), error.getDefaultMessage());
    }
}
