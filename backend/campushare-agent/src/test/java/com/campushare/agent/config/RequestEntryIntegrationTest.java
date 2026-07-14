package com.campushare.agent.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@DisplayName("Request Entry 集成测试")
class RequestEntryIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @Nested
    @DisplayName("X-Request-Id 检查")
    class RequestIdCheck {

        @Test
        @DisplayName("缺少 X-Request-Id 返回 400")
        void missingRequestId_returnsBadRequest() {
            webTestClient.post().uri("/api/chat")
                    .bodyValue("{\"sessionId\":\"test-session\",\"query\":\"hello\"}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("有 X-Request-Id 但无认证返回 401")
        void withRequestId_noAuth_returnsUnauthorized() {
            webTestClient.post().uri("/api/chat")
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .bodyValue("{\"sessionId\":\"test-session\",\"query\":\"hello\"}")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("限流配置接口")
    class RateLimitConfig {

        @Test
        @DisplayName("获取限流配置无需认证")
        void getRateLimitConfig_noAuthRequired() {
            webTestClient.get().uri("/agent/config/rate-limit")
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("获取限流状态无需认证")
        void getRateLimitStatus_noAuthRequired() {
            webTestClient.get().uri("/agent/config/rate-limit/status")
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    @DisplayName("Actuator 端点")
    class Actuator {

        @Test
        @DisplayName("actuator 健康检查无需认证")
        void actuatorHealth_noAuthRequired() {
            webTestClient.get().uri("/actuator/health")
                    .exchange()
                    .expectStatus().is2xxSuccessful();
        }
    }
}
