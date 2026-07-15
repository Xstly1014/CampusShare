package com.campushare.agent.security.impl;

import com.campushare.agent.dto.AuthContext;
import com.campushare.agent.security.AuthenticationProvider;
import com.campushare.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternalAuthenticationProvider implements AuthenticationProvider {

    @Value("${app.internal.token:campushare-internal-token}")
    private String internalToken;

    @Override
    public String getAuthType() {
        return "INTERNAL";
    }

    @Override
    public int getOrder() {
        return 3;
    }

    @Override
    public boolean supports(ServerHttpRequest request) {
        return request.getHeaders().containsKey("X-Internal-Token");
    }

    @Override
    public Mono<AuthContext> authenticate(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst("X-Internal-Token");
        if (!internalToken.equals(token)) {
            return Mono.error(new BusinessException(4001, "Invalid internal token"));
        }
        return Mono.just(AuthContext.builder()
                .userId("internal")
                .authType("INTERNAL")
                .roles(Set.of("INTERNAL"))
                .permissions(Set.of("ALL"))
                .clientIp(getClientIp(request))
                .deviceId(request.getHeaders().getFirst("X-Device-Id"))
                .appVersion(request.getHeaders().getFirst("X-App-Version"))
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