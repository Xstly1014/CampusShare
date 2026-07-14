package com.campushare.agent.security;

import com.campushare.agent.dto.AuthContext;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

public interface AuthenticationProvider {
    String getAuthType();
    Mono<AuthContext> authenticate(ServerHttpRequest request);
    boolean supports(ServerHttpRequest request);
    int getOrder();
}