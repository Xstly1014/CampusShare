package com.campushare.agent.security.impl;

import com.campushare.agent.dto.AuthContext;
import com.campushare.agent.security.AuthenticationProvider;
import com.campushare.common.exception.BusinessException;
import com.campushare.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationProvider implements AuthenticationProvider {

    private final JwtUtils jwtUtils;

    @Override
    public String getAuthType() {
        return "JWT";
    }

    @Override
    public int getOrder() {
        return 1;
    }

    @Override
    public boolean supports(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        return authHeader != null && authHeader.startsWith("Bearer ");
    }

    @Override
    public Mono<AuthContext> authenticate(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst("Authorization");
        String token = authHeader.substring(7);

        return Mono.fromCallable(() -> {
            Claims claims = jwtUtils.parseToken(token);
            String userId = jwtUtils.getUserId(token);
            String username = jwtUtils.getUsername(token);

            return AuthContext.builder()
                    .userId(userId)
                    .authType("JWT")
                    .roles(parseRoles(claims))
                    .permissions(parsePermissions(claims))
                    .expireTime(claims.getExpiration().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDateTime())
                    .clientIp(getClientIp(request))
                    .isVip(parseVip(claims))
                    .build();
        }).subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(e -> new BusinessException(4001, "Token验证失败"));
    }

    private Set<String> parseRoles(Claims claims) {
        Set<String> roles = new HashSet<>();
        Object rolesObj = claims.get("roles");
        if (rolesObj instanceof java.util.List) {
            ((java.util.List<?>) rolesObj).forEach(r -> roles.add(String.valueOf(r)));
        }
        return roles;
    }

    private Set<String> parsePermissions(Claims claims) {
        Set<String> permissions = new HashSet<>();
        Object permsObj = claims.get("permissions");
        if (permsObj instanceof java.util.List) {
            ((java.util.List<?>) permsObj).forEach(p -> permissions.add(String.valueOf(p)));
        }
        return permissions;
    }

    private boolean parseVip(Claims claims) {
        Object vipObj = claims.get("vip");
        return vipObj != null && Boolean.TRUE.equals(vipObj);
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