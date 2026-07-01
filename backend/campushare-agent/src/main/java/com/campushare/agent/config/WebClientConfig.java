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

@Configuration
public class WebClientConfig {

    @Value("${app.llm.deepseek.base-url}")
    private String baseUrl;

    @Value("${app.llm.deepseek.api-key}")
    private String apiKey;

    @Value("${app.llm.deepseek.timeout:60000}")
    private int timeoutMs;

    @Value("${app.llm.deepseek.connection-pool.max-connections:50}")
    private int maxConnections;

    @Value("${app.llm.deepseek.connection-pool.pending-acquire-timeout:45000}")
    private int pendingAcquireTimeoutMs;

    @Bean
    public WebClient deepSeekWebClient() {
        ConnectionProvider provider = ConnectionProvider.builder("deepseek-pool")
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
