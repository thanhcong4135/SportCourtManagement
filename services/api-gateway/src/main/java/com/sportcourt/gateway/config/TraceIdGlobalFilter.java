package com.sportcourt.gateway.config;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    public static final String TRACE_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incomingTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_HEADER);
        final String traceId;
        if (incomingTraceId == null || incomingTraceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        } else {
            traceId = incomingTraceId;
        }

        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(exchange.getRequest().mutate().headers(headers -> headers.set(TRACE_HEADER, traceId)).build())
            .build();
        mutatedExchange.getResponse().getHeaders().set(TRACE_HEADER, traceId);
        mutatedExchange.getAttributes().put(TRACE_HEADER, traceId);

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
