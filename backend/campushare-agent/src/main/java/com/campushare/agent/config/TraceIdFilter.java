package com.campushare.agent.config;

import com.campushare.agent.service.TraceService;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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

    private volatile Tracer tracer;

    private Tracer getTracer() {
        if (tracer == null) {
            synchronized (this) {
                if (tracer == null) {
                    tracer = GlobalOpenTelemetry.getTracer("campushare-agent", "1.0.0");
                }
            }
        }
        return tracer;
    }

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

        Span span = getTracer().spanBuilder("agent-request")
                .setAttribute("request.path", exchange.getRequest().getPath().value())
                .setAttribute("request.method", exchange.getRequest().getMethod().name())
                .setAttribute("request.traceId", traceId)
                .startSpan();

        final Scope scope = span.makeCurrent();

        return chain.filter(exchange)
                .contextWrite(context -> context.put(TRACE_ID_CONTEXT_KEY, traceId))
                .doOnSubscribe(subscription -> {
                    MDC.put("traceId", traceId.substring(0, 8));
                })
                .doFinally(signalType -> {
                    MDC.remove("traceId");
                    if (signalType == reactor.core.publisher.SignalType.ON_ERROR) {
                        span.recordException(new RuntimeException("Request failed"));
                        span.setStatus(StatusCode.ERROR);
                    }
                    span.end();
                    scope.close();
                });
    }

    public static String getTraceIdFromContext(reactor.util.context.Context context) {
        return context.getOrDefault(TRACE_ID_CONTEXT_KEY, null);
    }
}
