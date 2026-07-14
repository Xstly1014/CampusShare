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
}
