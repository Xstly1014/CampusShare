package com.campushare.agent.config;

import com.campushare.agent.service.TraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class TraceIdFilter implements WebFilter {

    private final TraceService traceService;

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String TRACE_ID_CONTEXT_KEY = "traceId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incomingTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        final String traceId;

        if (incomingTraceId == null || incomingTraceId.isBlank()) {
            traceId = traceService.generateTraceId();
            log.debug("Generated new traceId: {}", traceId.substring(0, 8));
        } else {
            traceId = incomingTraceId;
            log.debug("Received existing traceId: {}", traceId.substring(0, 8));
        }

        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, traceId);

        return chain.filter(exchange)
                .contextWrite(context -> context.put(TRACE_ID_CONTEXT_KEY, traceId))
                .doOnSubscribe(subscription -> {
                    MDC.put("traceId", traceId.substring(0, 8));
                })
                .doFinally(signalType -> {
                    MDC.remove("traceId");
                });
    }

    public static String getTraceIdFromContext(reactor.util.context.Context context) {
        return context.getOrDefault(TRACE_ID_CONTEXT_KEY, null);
    }
}