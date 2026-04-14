package com.sportcourt.gateway.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportcourt.gateway.api.ApiError;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ApiSecurityErrorHandler implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final List<String> allowedOrigins;

    public ApiSecurityErrorHandler(
        ObjectMapper objectMapper,
        @Value("${app.security.cors.allowed-origins:http://localhost:5173}") String allowedOriginsRaw
    ) {
        this.objectMapper = objectMapper;
        this.allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
            .map(String::trim)
            .filter(origin -> !origin.isEmpty())
            .collect(Collectors.toList());
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        return writeError(exchange, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return writeError(exchange, HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    private Mono<Void> writeError(ServerWebExchange exchange,
                                  HttpStatus status,
                                  String code,
                                  String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        appendCorsHeaders(exchange);

        String traceId = resolveTraceId(exchange);
        exchange.getResponse().getHeaders().set(TraceIdGlobalFilter.TRACE_HEADER, traceId);

        ApiError error = new ApiError(
            code,
            message,
            null,
            traceId,
            OffsetDateTime.now(),
            status.value(),
            exchange.getRequest().getPath().value(),
            status.getReasonPhrase()
        );

        DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
        DataBuffer dataBuffer = bufferFactory.wrap(toBytes(error));
        return exchange.getResponse().writeWith(Mono.just(dataBuffer));
    }

    private void appendCorsHeaders(ServerWebExchange exchange) {
        String origin = exchange.getRequest().getHeaders().getOrigin();
        if (origin == null || origin.isBlank()) {
            return;
        }

        if (!isAllowedOrigin(origin)) {
            return;
        }

        exchange.getResponse().getHeaders().set("Access-Control-Allow-Origin", origin);
        exchange.getResponse().getHeaders().set("Access-Control-Allow-Credentials", "true");
        exchange.getResponse().getHeaders().set("Access-Control-Expose-Headers", TraceIdGlobalFilter.TRACE_HEADER);
        exchange.getResponse().getHeaders().add("Vary", "Origin");
    }

    private boolean isAllowedOrigin(String origin) {
        return allowedOrigins.contains("*") || allowedOrigins.contains(origin);
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId = request.getHeaders().getFirst(TraceIdGlobalFilter.TRACE_HEADER);
        if (traceId == null || traceId.isBlank()) {
            Object traceAttribute = exchange.getAttribute(TraceIdGlobalFilter.TRACE_HEADER);
            if (traceAttribute != null) {
                traceId = traceAttribute.toString();
            }
        }
        return (traceId == null || traceId.isBlank()) ? UUID.randomUUID().toString() : traceId;
    }

    private byte[] toBytes(ApiError error) {
        try {
            return objectMapper.writeValueAsBytes(error);
        } catch (JsonProcessingException e) {
            String fallback = "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Unexpected server error\"}";
            return fallback.getBytes(StandardCharsets.UTF_8);
        }
    }
}
