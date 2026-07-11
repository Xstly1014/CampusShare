package com.campushare.agent.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class PostWebClientConfig {

    @Value("${service.post.url:http://localhost:8082}")
    private String baseUrl;

    @Value("${app.post-sync.timeout:30000}")
    private int timeoutMs;

    @Value("${app.post-sync.connection-pool.max-connections:20}")
    private int maxConnections;

    @Value("${app.post-sync.connection-pool.pending-acquire-timeout:30000}")
    private int pendingAcquireTimeoutMs;

    @Bean
    public WebClient postWebClient() {
        ConnectionProvider provider = ConnectionProvider.builder("post-sync-pool")
                .maxConnections(maxConnections)
                .pendingAcquireMaxCount(maxConnections * 2)
                .pendingAcquireTimeout(Duration.ofMillis(pendingAcquireTimeoutMs))
                .maxIdleTime(Duration.ofSeconds(30))
                .maxLifeTime(Duration.ofMinutes(5))
                .evictInBackground(Duration.ofSeconds(60))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000)
                .responseTimeout(Duration.ofMillis(timeoutMs))
                .metrics(true, uri -> "post-sync")
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                            .addHandlerLast(new WriteTimeoutHandler(timeoutMs, TimeUnit.MILLISECONDS))
                )
                .doOnRequest((req, conn) -> log.debug("Post-sync request: {} {}", req.method(), req.uri()))
                .doOnResponse((resp, conn) -> log.debug("Post-sync response: {}", resp.status()));

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Content-Type", "application/json")
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .filter(errorHandler("PostSync"))
                .build();
    }

    private ExchangeFilterFunction errorHandler(String clientName) {
        return ExchangeFilterFunction.ofResponseProcessor(clientResponse -> {
            HttpStatus status = HttpStatus.valueOf(clientResponse.statusCode().value());
            if (status.is4xxClientError() || status.is5xxServerError()) {
                return clientResponse.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .flatMap(body -> {
                            log.error("{} API error: status={}, body={}", clientName, status, body);
                            return Mono.error(new RuntimeException(clientName + " API error: HTTP " + status.value()));
                        });
            }
            return Mono.just(clientResponse);
        });
    }
}
