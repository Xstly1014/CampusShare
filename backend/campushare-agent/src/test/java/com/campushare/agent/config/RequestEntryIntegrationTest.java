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
    @DisplayName("白名单路径")
    class WhitelistPaths {

        @Test
        @DisplayName("actuator 路径无需认证")
        void actuatorEndpoint_noAuthRequired() {
            webTestClient.get().uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk();
        }

        @Test
        @DisplayName("rate-limit 配置接口无需认证")
        void rateLimitConfigEndpoint_noAuthRequired() {
            webTestClient.get().uri("/api/rate-limit/config")
                    .exchange()
                    .expectStatus().isOk();
        }
    }

    @Nested
    @DisplayName("认证测试")
    class Authentication {

        @Test
        @DisplayName("无认证信息访问 /api/chat 返回 401")
        void chatEndpoint_withoutAuth() {
            webTestClient.post().uri("/api/chat")
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .bodyValue("{\"sessionId\":\"test-session\",\"query\":\"hello\"}")
                    .exchange()
                    .expectStatus().isUnauthorized();
        }
    }

    @Nested
    @DisplayName("防重放测试")
    class AntiReplay {

        @Test
        @DisplayName("缺少 X-Request-Id 返回 400")
        void chatEndpoint_missingRequestId() {
            webTestClient.post().uri("/api/chat")
                    .bodyValue("{\"sessionId\":\"test-session\",\"query\":\"hello\"}")
                    .exchange()
                    .expectStatus().isBadRequest();
        }

        @Test
        @DisplayName("actuator 路径不需要 X-Request-Id")
        void actuatorEndpoint_noRequestId() {
            webTestClient.get().uri("/actuator/health")
                    .exchange()
                    .expectStatus().isOk();
        }
    }
}
