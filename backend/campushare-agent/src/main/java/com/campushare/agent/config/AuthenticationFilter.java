package com.campushare.agent.config;

import com.campushare.agent.dto.AuthContext;
import com.campushare.agent.security.AuthenticationProvider;
import com.campushare.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@RequiredArgsConstructor
public class AuthenticationFilter implements WebFilter {

    private final List<AuthenticationProvider> providers;

    public static final String AUTH_CONTEXT_KEY = "authContext";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/actuator/") || path.startsWith("/api/rate-limit/")) {
            return chain.filter(exchange);
        }

        return Mono.just(exchange.getRequest())
                .flatMap(request -> {
                    Optional<AuthenticationProvider> provider = providers.stream()
                            .filter(p -> p.supports(request))
                            .min((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));

                    if (provider.isEmpty()) {
                        log.warn("No authentication provider found for path: {}", path);
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        return exchange.getResponse().setComplete();
                    }

                    return provider.get().authenticate(request);
                })
                .cast(AuthContext.class)
                .doOnNext(ctx -> {
                    exchange.getAttributes().put(AUTH_CONTEXT_KEY, ctx);
                    log.debug("Authentication successful, userId={}, authType={}", ctx.getUserId(), ctx.getAuthType());
                })
                .then(chain.filter(exchange))
                .onErrorResume(e -> {
                    log.warn("Authentication failed: {}", e.getMessage());
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                });
    }
}