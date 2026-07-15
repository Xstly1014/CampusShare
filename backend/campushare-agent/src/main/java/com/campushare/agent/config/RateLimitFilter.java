package com.campushare.agent.config;

import com.campushare.agent.dto.AuthContext;
import com.campushare.agent.dto.RateLimitResult;
import com.campushare.agent.service.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
@RequiredArgsConstructor
public class RateLimitFilter implements WebFilter {

    private final RateLimitService rateLimitService;
    private final com.campushare.agent.service.SecurityAuditService securityAuditService;

    @Value("${app.rate-limit.global.max-requests:1000}")
    private int globalMaxRequests;

    @Value("${app.rate-limit.global.window-seconds:60}")
    private int globalWindowSeconds;

    @Value("${app.rate-limit.user.max-requests:60}")
    private int userMaxRequests;

    @Value("${app.rate-limit.user.window-seconds:60}")
    private int userWindowSeconds;

    @Value("${app.rate-limit.ip.max-requests:500}")
    private int ipMaxRequests;

    @Value("${app.rate-limit.ip.window-seconds:60}")
    private int ipWindowSeconds;

    @Value("${app.rate-limit.session.max-requests:30}")
    private int sessionMaxRequests;

    @Value("${app.rate-limit.session.window-seconds:60}")
    private int sessionWindowSeconds;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/actuator/")
                || path.startsWith("/internal/")
                || path.equals("/agent/config/rate-limit")
                || path.startsWith("/agent/config/rate-limit/")) {
            return chain.filter(exchange);
        }

        AuthContext authContext = exchange.getAttribute(AuthenticationFilter.AUTH_CONTEXT_KEY);

        if (authContext != null && authContext.isVip()) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(exchange);

        List<String> keys = new ArrayList<>();
        List<Integer> maxRequests = new ArrayList<>();

        keys.add("global");
        maxRequests.add(globalMaxRequests);

        keys.add("ip:" + clientIp);
        maxRequests.add(ipMaxRequests);

        if (authContext != null) {
            keys.add("user:" + authContext.getUserId());
            maxRequests.add(userMaxRequests);
        }

        String sessionId = exchange.getRequest().getHeaders().getFirst("X-Session-Id");
        if (sessionId != null && !sessionId.isBlank()) {
            keys.add("session:" + sessionId);
            maxRequests.add(sessionMaxRequests);
        }

        return rateLimitService.checkMultiRateLimit(keys, maxRequests, globalWindowSeconds)
                .flatMap(result -> {
                    if (result.isAllowed()) {
                        return chain.filter(exchange);
                    } else {
                        String exceededKey = result.getExceededKey();
                        log.warn("Rate limit exceeded, key={}, current={}, max={}",
                                exceededKey, result.getCurrent(), result.getMax());
                        // Async audit log
                        String userId = authContext != null ? authContext.getUserId() : "anonymous";
                        Mono.fromRunnable(() -> securityAuditService.logThreat(
                                null, userId, null, "RATE_LIMIT_EXCEEDED", "WARN",
                                "Key=" + exceededKey + ", current=" + result.getCurrent() + ", max=" + result.getMax(),
                                "BLOCKED", null, true
                        )).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()).subscribe();
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                        return exchange.getResponse().setComplete();
                    }
                });
    }

    private String getClientIp(ServerWebExchange exchange) {
        AuthContext authContext = exchange.getAttribute(AuthenticationFilter.AUTH_CONTEXT_KEY);
        if (authContext != null && authContext.getClientIp() != null) {
            return authContext.getClientIp();
        }
        return exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }
}