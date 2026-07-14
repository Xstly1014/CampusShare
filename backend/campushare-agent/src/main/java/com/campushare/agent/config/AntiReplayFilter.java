package com.campushare.agent.config;

import com.campushare.agent.dto.AuthContext;
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

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
@RequiredArgsConstructor
public class AntiReplayFilter implements WebFilter {

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String REPLAY_PREFIX = "agent:replay:";
    private static final int REPLAY_TTL = 300;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/actuator/")) {
            return chain.filter(exchange);
        }

        String requestId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
        AuthContext authContext = exchange.getAttribute(AuthenticationFilter.AUTH_CONTEXT_KEY);

        if (requestId == null || requestId.isBlank()) {
            log.warn("X-Request-Id is required");
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        if (authContext == null) {
            return chain.filter(exchange);
        }

        String replayKey = REPLAY_PREFIX + authContext.getUserId() + ":" + requestId;

        return redisTemplate.opsForValue().setIfAbsent(replayKey, "1", Duration.ofSeconds(REPLAY_TTL))
                .flatMap(set -> {
                    if (Boolean.TRUE.equals(set)) {
                        return chain.filter(exchange);
                    } else {
                        log.warn("Duplicate request detected, userId={}, requestId={}", authContext.getUserId(), requestId);
                        exchange.getResponse().setStatusCode(HttpStatus.CONFLICT);
                        return exchange.getResponse().setComplete();
                    }
                });
    }
}