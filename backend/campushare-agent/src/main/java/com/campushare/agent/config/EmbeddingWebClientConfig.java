package com.campushare.agent.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 硅基流动 Embedding API 的 WebClient 配置。
 *
 * 参考 WebClientConfig（DeepSeek WebClient）模式：
 * - 独立连接池（embedding-pool），避免与 DeepSeek 请求互相影响
 * - Authorization Bearer header 预设
 * - 独立超时控制（embedding 调用比 chat 快，但批量调用可能较慢）
 */
@Configuration
public class EmbeddingWebClientConfig {

    @Value("${app.llm.embedding.base-url}")
    private String baseUrl;

    @Value("${app.llm.embedding.api-key}")
    private String apiKey;

    @Value("${app.llm.embedding.timeout:30000}")
    private int timeoutMs;

    @Value("${app.llm.embedding.connection-pool.max-connections:30}")
    private int maxConnections;

    @Value("${app.llm.embedding.connection-pool.pending-acquire-timeout:30000}")
    private int pendingAcquireTimeoutMs;

    @Bean
    public WebClient embeddingWebClient() {
        ConnectionProvider provider = ConnectionProvider.builder("embedding-pool")
                .maxConnections(maxConnections)
                .pendingAcquireTimeout(Duration.ofMillis(pendingAcquireTimeoutMs))
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .evictInBackground(Duration.ofSeconds(60))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                            .addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                );

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }
}
