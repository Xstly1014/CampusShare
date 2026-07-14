package com.campushare.agent.security.impl;

import com.campushare.agent.dto.AuthContext;
import com.campushare.agent.security.AuthenticationProvider;
import com.campushare.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    private final ReactiveStringRedisTemplate redisTemplate;

    @Override
    public String getAuthType() {
        return "API_KEY";
    }

    @Override
    public int getOrder() {
        return 2;
    }

    @Override
    public boolean supports(ServerHttpRequest request) {
        return request.getHeaders().containsKey("X-API-Key");
    }

    @Override
    public Mono<AuthContext> authenticate(ServerHttpRequest request) {
        String apiKey = request.getHeaders().getFirst("X-API-Key");
        return redisTemplate.opsForValue().get("agent:api:key:" + apiKey)
                .switchIfEmpty(Mono.error(new BusinessException(4001, "Invalid API Key")))
                .map(userId -> AuthContext.builder()
                        .userId(userId)
                        .authType("API_KEY")
                        .roles(Set.of("API_USER"))
                        .permissions(Set.of("CHAT"))
                        .clientIp(getClientIp(request))
                        .build());
    }

    private String getClientIp(ServerHttpRequest request) {
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }
}