package com.campushare.agent.config;

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

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InternalApiAuthFilter implements WebFilter {

    @Value("${app.internal.token:campushare-internal-token}")
    private String internalToken;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        if (path.startsWith("/internal/")) {
            String token = exchange.getRequest().getHeaders().getFirst("X-Internal-Token");
            if (token == null || !internalToken.equals(token)) {
                log.warn("Unauthorized internal API access attempt from {}, path={}",
                        exchange.getRequest().getRemoteAddress(), path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }
        }

        return chain.filter(exchange);
    }
}
