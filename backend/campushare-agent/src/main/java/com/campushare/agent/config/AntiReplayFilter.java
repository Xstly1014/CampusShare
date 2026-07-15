package com.campushare.agent.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class AntiReplayFilter implements WebFilter {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REPLAY_PREFIX = "agent:replay:";
    private static final int REPLAY_TTL = 300;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/actuator/")) {
            return chain.filter(exchange);
        }

        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString().replace("-", "");
        }

        exchange.getResponse().getHeaders().add(REQUEST_ID_HEADER, requestId);

        final String finalRequestId = requestId;
        String clientIp = getClientIp(exchange);
        String replayKey = REPLAY_PREFIX + clientIp + ":" + finalRequestId;

        return redisTemplate.opsForValue().setIfAbsent(replayKey, "1", Duration.ofSeconds(REPLAY_TTL))
                .flatMap(set -> {
                    if (Boolean.TRUE.equals(set)) {
                        ServerWebExchange mutatedExchange = exchange.mutate()
                                .request(builder -> builder.header(REQUEST_ID_HEADER, finalRequestId))
                                .build();
                        return chain.filter(mutatedExchange);
                    } else {
                        log.warn("Duplicate request detected, clientIp={}, requestId={}", clientIp, finalRequestId);
                        exchange.getResponse().setStatusCode(HttpStatus.CONFLICT);
                        return exchange.getResponse().setComplete();
                    }
                });
    }

    private String getClientIp(ServerWebExchange exchange) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        if (exchange.getRequest().getRemoteAddress() != null) {
            return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        }
        return "anonymous";
    }
}
