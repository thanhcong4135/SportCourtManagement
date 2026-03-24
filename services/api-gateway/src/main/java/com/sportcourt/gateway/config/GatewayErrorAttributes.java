package com.sportcourt.gateway.config;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.DefaultErrorAttributes;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
@Order(-2)
public class GatewayErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(ServerRequest request, ErrorAttributeOptions options) {
        Throwable error = getError(request);
        HttpStatus status = resolveStatus(error);
        String traceId = resolveTraceId(request);

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("code", codeForStatus(status));
        attributes.put("message", messageFor(error, status));
        attributes.put("details", null);
        attributes.put("traceId", traceId);
        attributes.put("timestamp", OffsetDateTime.now().toString());
        attributes.put("status", status.value());
        attributes.put("path", request.path());
        attributes.put("error", status.getReasonPhrase());
        return attributes;
    }

    private HttpStatus resolveStatus(Throwable error) {
        if (error instanceof ResponseStatusException responseStatusException) {
            return HttpStatus.valueOf(responseStatusException.getStatusCode().value());
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private String messageFor(Throwable error, HttpStatus status) {
        if (error instanceof ResponseStatusException responseStatusException
            && responseStatusException.getReason() != null
            && !responseStatusException.getReason().isBlank()) {
            return responseStatusException.getReason();
        }
        return status == HttpStatus.INTERNAL_SERVER_ERROR
            ? "Unexpected gateway error"
            : status.getReasonPhrase();
    }

    private String codeForStatus(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            default -> "GATEWAY_ERROR";
        };
    }

    private String resolveTraceId(ServerRequest request) {
        String traceId = request.headers().firstHeader(TraceIdGlobalFilter.TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            Object traceAttribute = request.attributes().get(TraceIdGlobalFilter.TRACE_HEADER);
            if (traceAttribute != null) {
                traceId = traceAttribute.toString();
            }
        }
        return (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
    }
}
