package com.campushare.agent.controller;

import com.campushare.agent.dto.ChatRequest;
import com.campushare.agent.dto.SessionCreateRequest;
import com.campushare.agent.dto.SessionResponse;
import com.campushare.agent.dto.TurnResponse;
import com.campushare.agent.service.AgentChatService;
import com.campushare.agent.service.AgentRateLimiter;
import com.campushare.agent.service.AgentSessionService;
import com.campushare.common.result.Result;
import com.campushare.common.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentChatService chatService;
    private final AgentSessionService sessionService;
    private final AgentRateLimiter rateLimiter;
    private final JwtUtils jwtUtils;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(
            @RequestHeader("Authorization") String token,
            @RequestBody ChatRequest request) {

        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));

        return rateLimiter.checkRateLimit(userId)
                .flatMapMany(allowed -> {
                    if (!allowed) {
                        return Flux.just(ServerSentEvent.<String>builder()
                                .event("error")
                                .data("请求过于频繁，每分钟最多 10 次，请稍后再试")
                                .build());
                    }
                    return chatService.chat(userId, request)
                            .map(event -> ServerSentEvent.<String>builder()
                                    .event(event.type())
                                    .data(event.data())
                                    .build())
                            .concatWith(Mono.fromSupplier(() -> ServerSentEvent.<String>builder()
                                    .event("done")
                                    .data("[DONE]")
                                    .build()))
                            .onErrorResume(e -> {
                                log.error("Chat error", e);
                                return Flux.just(ServerSentEvent.<String>builder()
                                        .event("error")
                                        .data(e.getMessage() != null ? e.getMessage() : "服务异常")
                                        .build());
                            });
                });
    }

    @PostMapping("/sessions")
    public Mono<Result<SessionResponse>> createSession(
            @RequestHeader("Authorization") String token,
            @RequestBody(required = false) SessionCreateRequest request) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Mono.fromCallable(() -> sessionService.createSession(userId,
                        request != null ? request : new SessionCreateRequest()))
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/sessions")
    public Mono<Result<List<SessionResponse>>> getSessions(
            @RequestHeader("Authorization") String token) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Mono.fromCallable(() -> sessionService.getUserSessions(userId))
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/sessions/{sessionId}")
    public Mono<Result<SessionResponse>> getSession(
            @RequestHeader("Authorization") String token,
            @PathVariable String sessionId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Mono.fromCallable(() -> sessionService.getSession(userId, sessionId))
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/sessions/{sessionId}/turns")
    public Mono<Result<List<TurnResponse>>> getSessionTurns(
            @RequestHeader("Authorization") String token,
            @PathVariable String sessionId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Mono.fromCallable(() -> sessionService.getSessionTurns(userId, sessionId))
                .map(Result::success)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/sessions/{sessionId}/archive")
    public Mono<Result<Void>> archiveSession(
            @RequestHeader("Authorization") String token,
            @PathVariable String sessionId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Mono.fromRunnable(() -> sessionService.archiveSession(userId, sessionId))
                .thenReturn(Result.<Void>success(null))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Mono<Result<Void>> deleteSession(
            @RequestHeader("Authorization") String token,
            @PathVariable String sessionId) {
        String userId = jwtUtils.getUserId(token.replace("Bearer ", ""));
        return Mono.fromRunnable(() -> sessionService.deleteSession(userId, sessionId))
                .thenReturn(Result.<Void>success(null))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
