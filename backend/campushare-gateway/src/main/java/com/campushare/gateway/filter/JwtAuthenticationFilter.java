package com.campushare.gateway.filter;

import cn.hutool.core.util.StrUtil;
import com.campushare.gateway.utils.JwtUtils;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
    
    private final JwtUtils jwtUtils;
    
    private static final List<String> WHITE_LIST = Arrays.asList(
        "/api/auth/login",
        "/api/auth/register",
        "/api/auth/send-code",
        "/api/auth/reset-password",
        "/api/auth/refresh-token",
        "/api/posts/school",
        "/api/posts/",
        "/api/files/",
        "/api/admin/"
    );
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();
        
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }
        
        String token = extractToken(request);
        if (StrUtil.isBlank(token)) {
            return unauthorized(exchange.getResponse(), "未提供认证令牌");
        }
        
        try {
            if (!jwtUtils.validateToken(token)) {
                return unauthorized(exchange.getResponse(), "令牌无效或已过期");
            }
            
            if (!jwtUtils.isAccessToken(token)) {
                return unauthorized(exchange.getResponse(), "无效的令牌类型");
            }
            
            String userId = jwtUtils.getUserId(token);
            String username = jwtUtils.getUsername(token);
            
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", userId)
                    .header("X-Username", username)
                    .build();
            
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
            
        } catch (JwtException e) {
            log.error("JWT验证失败: {}", e.getMessage());
            return unauthorized(exchange.getResponse(), "令牌验证失败");
        }
    }
    
    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(path::startsWith);
    }
    
    private String extractToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst("Authorization");
        if (StrUtil.isNotBlank(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
    
    private Mono<Void> unauthorized(ServerHttpResponse response, String message) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json");
        String body = String.format("{\"code\":401,\"message\":\"%s\"}", message);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes())));
    }
    
    @Override
    public int getOrder() {
        return -100;
    }
}
