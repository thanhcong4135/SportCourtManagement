package com.sportcourt.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.auth.api.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class ApiSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String TRACE_HEADER = "X-Trace-Id";

    private final ObjectMapper objectMapper;

    public ApiSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        writeError(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writeError(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    private void writeError(HttpServletRequest request,
                            HttpServletResponse response,
                            HttpStatus status,
                            String code,
                            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        String traceId = resolveTraceId(request);
        response.setHeader(TRACE_HEADER, traceId);

        ApiError error = new ApiError(
            code,
            message,
            null,
            traceId,
            OffsetDateTime.now(),
            status.value(),
            request.getRequestURI(),
            status.getReasonPhrase()
        );
        objectMapper.writeValue(response.getOutputStream(), error);
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
}
