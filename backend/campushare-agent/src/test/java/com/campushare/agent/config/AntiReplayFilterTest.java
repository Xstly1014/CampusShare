package com.campushare.agent.config;

import com.campushare.agent.dto.AuthContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.MockServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.adapter.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("AntiReplayFilter 单元测试")
class AntiReplayFilterTest {

    @Autowired
    private AntiReplayFilter antiReplayFilter;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    private ServerWebExchange createExchange(String path, String requestId, String userId) {
        MockServerHttpRequest request = MockServerHttpRequest.post(path)
                .header("X-Request-Id", requestId)
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        if (userId != null) {
            exchange.getAttributes().put(AuthenticationFilter.AUTH_CONTEXT_KEY,
                    AuthContext.builder().userId(userId).authType("JWT").build());
        }
        return exchange;
    }

    @BeforeEach
    void setUp() {
        redisTemplate.keys("agent:replay:*").flatMap(redisTemplate::delete).blockLast();
    }

    @Nested
    @DisplayName("重复请求检测")
    class DuplicateRequest {

        @Test
        @DisplayName("相同 userId 和 requestId 第二次请求被拦截")
        void filter_duplicateRequest() {
            String requestId = "unique-request-id-001";
            String userId = "user-123";

            ServerWebExchange exchange1 = createExchange("/api/chat", requestId, userId);
            ServerWebExchange exchange2 = createExchange("/api/chat", requestId, userId);

            StepVerifier.create(antiReplayFilter.filter(exchange1, e -> Mono.empty()))
                    .verifyComplete();

            StepVerifier.create(antiReplayFilter.filter(exchange2, e -> Mono.empty()))
                    .verifyComplete();

            assertThat(exchange2.getResponse().getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        @DisplayName("首次请求正常通过")
        void filter_firstRequest() {
            ServerWebExchange exchange = createExchange("/api/chat", "first-request-id", "user-123");

            StepVerifier.create(antiReplayFilter.filter(exchange, e -> Mono.empty()))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("不同 requestId 都正常通过")
        void filter_differentRequestId() {
            ServerWebExchange exchange1 = createExchange("/api/chat", "request-001", "user-123");
            ServerWebExchange exchange2 = createExchange("/api/chat", "request-002", "user-123");

            StepVerifier.create(antiReplayFilter.filter(exchange1, e -> Mono.empty()))
                    .verifyComplete();

            StepVerifier.create(antiReplayFilter.filter(exchange2, e -> Mono.empty()))
                    .verifyComplete();

            assertThat(exchange2.getResponse().getStatusCode()).isNull();
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("缺少 X-Request-Id 返回 400")
        void filter_missingRequestId() {
            ServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/chat").build());
            exchange.getAttributes().put(AuthenticationFilter.AUTH_CONTEXT_KEY,
                    AuthContext.builder().userId("user-123").authType("JWT").build());

            StepVerifier.create(antiReplayFilter.filter(exchange, e -> Mono.empty()))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("actuator 路径跳过检查")
        void filter_actuatorPath() {
            ServerWebExchange exchange = createExchange("/actuator/health", "actuator-request", "user-123");

            StepVerifier.create(antiReplayFilter.filter(exchange, e -> Mono.empty()))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }

        @Test
        @DisplayName("无 AuthContext 跳过检查")
        void filter_noAuthContext() {
            ServerWebExchange exchange = MockServerWebExchange.from(
                    MockServerHttpRequest.post("/api/chat")
                            .header("X-Request-Id", "no-auth-request")
                            .build());

            StepVerifier.create(antiReplayFilter.filter(exchange, e -> Mono.empty()))
                    .verifyComplete();

            assertThat(exchange.getResponse().getStatusCode()).isNull();
        }
    }
}
