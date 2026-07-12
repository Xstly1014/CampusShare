package com.campushare.agent.controller;

import com.campushare.agent.dto.ChatRequest;
import com.campushare.agent.dto.MoveSessionCategoryRequest;
import com.campushare.agent.dto.SessionCreateRequest;
import com.campushare.agent.dto.SessionResponse;
import com.campushare.agent.dto.TurnResponse;
import com.campushare.agent.service.AgentChatService;
import com.campushare.agent.service.AgentRateLimiter;
import com.campushare.agent.service.AgentSessionService;
import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentChatService chatService;
    private final AgentSessionService sessionService;
    private final AgentRateLimiter rateLimiter;
    private final JwtUtils jwtUtils;
    private final MeterRegistry meterRegistry;

    private Counter sseConnectionsTotal;
    private Counter sseCancelledTotal;
    private Counter sseErrorsTotal;

    @jakarta.annotation.PostConstruct
    public void initMetrics() {
        sseConnectionsTotal = Counter.builder("agent.sse.connections.total")
                .description("Total SSE connections established")
                .register(meterRegistry);
        sseCancelledTotal = Counter.builder("agent.sse.cancelled.total")
                .description("Total SSE connections cancelled by client")
                .register(meterRegistry);
        sseErrorsTotal = Counter.builder("agent.sse.errors.total")
                .description("Total SSE stream errors")
                .register(meterRegistry);
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody ChatRequest request) {

        AtomicLong startTime = new AtomicLong(System.currentTimeMillis());
        String sessionId = request.getSessionId();

        return Mono.fromCallable(() -> jwtUtils.getUserId(token.replace("Bearer ", "")))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(userId -> rateLimiter.checkRateLimit(userId)
                        .flatMapMany(allowed -> {
                            if (!allowed) {
                                return Flux.just(ServerSentEvent.<String>builder()
                                        .event("error")
                                        .data("请求过于频繁，每分钟最多 10 次，请稍后再试")
                                        .build());
                            }

                            sseConnectionsTotal.increment();
                            log.info("SSE connection started, userId={}, sessionId={}", userId, sessionId);

                            return chatService.chat(userId, request)
                                    .map(event -> ServerSentEvent.<String>builder()
                                            .event(event.type())
                                            .data(event.data())
                                            .build())
                                    .onBackpressureBuffer(256, dropped -> {
                                        log.warn("SSE backpressure buffer full, dropping event for session {}", sessionId);
                                    })
                                    .concatWith(Mono.fromSupplier(() -> ServerSentEvent.<String>builder()
                                            .event("done")
                                            .data("[DONE]")
                                            .build()))
                                    .doOnCancel(() -> {
                                        sseCancelledTotal.increment();
                                        long duration = System.currentTimeMillis() - startTime.get();
                                        log.info("SSE connection cancelled by client, sessionId={}, duration={}ms", sessionId, duration);
                                    })
                                    .doOnComplete(() -> {
                                        long duration = System.currentTimeMillis() - startTime.get();
                                        log.info("SSE connection completed, sessionId={}, duration={}ms", sessionId, duration);
                                    });
                        }))
                .doOnError(e -> {
                    sseErrorsTotal.increment();
                    log.error("SSE stream error, sessionId={}", sessionId, e);
                })
                .onErrorResume(e -> Flux.just(ServerSentEvent.<String>builder()
                        .event("error")
                        .data(e.getMessage() != null ? e.getMessage() : "服务异常，请稍后重试")
                        .build()));
    }

    @PostMapping("/sessions")
    public Mono<Result<SessionResponse>> createSession(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody(required = false) SessionCreateRequest request) {
        return Mono.fromCallable(() -> {
                    String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
                    return sessionService.createSession(userId,
                            request != null ? request : new SessionCreateRequest());
                })
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/sessions")
    public Mono<Result<List<SessionResponse>>> getSessions(
            @RequestHeader("Authorization") String token) {
        return Mono.fromCallable(() -> {
                    String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
                    return sessionService.getUserSessions(userId);
                })
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/sessions/{sessionId}")
    public Mono<Result<SessionResponse>> getSession(
            @RequestHeader("Authorization") String token,
            @PathVariable String sessionId) {
        return Mono.fromCallable(() -> {
                    String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
                    return sessionService.getSession(userId, sessionId);
                })
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/sessions/{sessionId}/turns")
    public Mono<Result<List<TurnResponse>>> getSessionTurns(
            @RequestHeader("Authorization") String token,
            @PathVariable String sessionId) {
        return Mono.fromCallable(() -> {
                    String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
                    return sessionService.getSessionTurns(userId, sessionId);
                })
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/sessions/{sessionId}/archive")
    public Mono<Result<Void>> archiveSession(
            @RequestHeader("Authorization") String token,
            @PathVariable String sessionId) {
        return Mono.fromRunnable(() -> {
                    String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
                    sessionService.archiveSession(userId, sessionId);
                })
                .thenReturn(Result.<Void>success(null))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Mono<Result<Void>> deleteSession(
            @RequestHeader("Authorization") String token,
            @PathVariable String sessionId) {
        return Mono.fromRunnable(() -> {
                    String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
                    sessionService.deleteSession(userId, sessionId);
                })
                .thenReturn(Result.<Void>success(null))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/sessions/{sessionId}/category")
    public Mono<Result<SessionResponse>> moveSessionCategory(
            @RequestHeader("Authorization") String token,
            @PathVariable String sessionId,
            @Valid @RequestBody MoveSessionCategoryRequest request) {
        return Mono.fromCallable(() -> {
                    String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
                    return sessionService.moveSessionCategory(
                            userId, sessionId, request != null ? request.getCategoryId() : null);
                })
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }
}
