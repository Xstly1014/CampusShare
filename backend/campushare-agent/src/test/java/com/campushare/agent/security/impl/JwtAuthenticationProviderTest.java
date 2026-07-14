package com.campushare.agent.security.impl;

import com.campushare.agent.dto.AuthContext;
import com.campushare.common.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthenticationProvider 单元测试")
class JwtAuthenticationProviderTest {

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private JwtAuthenticationProvider jwtProvider;

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
}
