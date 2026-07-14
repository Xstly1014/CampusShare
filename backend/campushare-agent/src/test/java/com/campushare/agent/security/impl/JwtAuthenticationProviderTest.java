package com.campushare.agent.security.impl;

import com.campushare.agent.dto.AuthContext;
import com.campushare.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.MockServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationProvider 单元测试")
class JwtAuthenticationProviderTest {

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private JwtAuthenticationProvider jwtProvider;

    private ServerHttpRequest createRequestWithToken(String token) {
        return MockServerHttpRequest.get("/api/chat")
                .header("Authorization", "Bearer " + token)
                .build();
    }

    @Nested
    @DisplayName("supports 判断")
    class Supports {

        @Test
        @DisplayName("支持 Bearer Token 请求")
        void supports_withBearerToken() {
            ServerHttpRequest request = createRequestWithToken("test-token");

            assertThat(jwtProvider.supports(request)).isTrue();
        }

        @Test
        @DisplayName("不支持无 Authorization 头的请求")
        void supports_withoutAuthorization() {
            ServerHttpRequest request = MockServerHttpRequest.get("/api/chat").build();

            assertThat(jwtProvider.supports(request)).isFalse();
        }

        @Test
        @DisplayName("不支持非 Bearer Token")
        void supports_nonBearerToken() {
            ServerHttpRequest request = MockServerHttpRequest.get("/api/chat")
                    .header("Authorization", "Basic xxx")
                    .build();

            assertThat(jwtProvider.supports(request)).isFalse();
        }
}

@Nested
@DisplayName("authenticate 认证")
class Authenticate {

@Test
        @DisplayName("有效 Token 认证成功")
        void authenticate_validToken() {
            String token = "valid-jwt-token";
            String userId = "user-123";
            String username = "testuser";

            Claims claims = mockClaims(userId, username);
            when(jwtUtils.parseToken(token)).thenReturn(claims);
            when(jwtUtils.getUserId(token)).thenReturn(userId);
            when(jwtUtils.getUsername(token)).thenReturn(username);

            ServerHttpRequest request = createRequestWithToken(token);

            StepVerifier.create(jwtProvider.authenticate(request))
                    .expectNextMatches(ctx ->
                            userId.equals(ctx.getUserId()) &&
                            "JWT".equals(ctx.getAuthType()))
                    .verifyComplete();
        }

        @Test
        @DisplayName("解析失败返回错误")
        void authenticate_invalidToken() {
            String token = "invalid-token";
            when(jwtUtils.parseToken(token)).thenThrow(new RuntimeException("Invalid token"));

            ServerHttpRequest request = createRequestWithToken(token);

            StepVerifier.create(jwtProvider.authenticate(request))
                    .expectError()
                    .verify();
        }
    }

    @Nested
    @DisplayName("getOrder 优先级")
    class Order {

        @Test
        @DisplayName("优先级为 1")
        void getOrder_returns1() {
            assertThat(jwtProvider.getOrder()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("getAuthType 类型")
    class AuthType {

        @Test
        @DisplayName("返回 JWT")
        void getAuthType_returnsJWT() {
            assertThat(jwtProvider.getAuthType()).isEqualTo("JWT");
        }
    }

    private Claims mockClaims(String userId, String username) {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("username", username);
        map.put("roles", List.of("USER"));
        map.put("permissions", List.of("read"));
        map.put("vip", false);

        Claims claims = io.jsonwebtoken.Jwts.claims(map);
        claims.setExpiration(new Date(System.currentTimeMillis() + 3600000));
        return claims;
    }
}
