package com.campushare.agent.config;

import com.campushare.agent.exception.AuthenticationException;
import com.campushare.agent.exception.AuthorizationException;
import com.campushare.agent.exception.BadRequestException;
import com.campushare.agent.exception.ConflictException;
import com.campushare.agent.exception.RateLimitException;
import com.campushare.agent.exception.ReplayDetectedException;
import com.campushare.agent.exception.ServiceUnavailableException;
import com.campushare.agent.exception.TimeoutException;
import com.campushare.common.exception.BusinessException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class AgentGlobalExceptionHandler {

    @ExceptionHandler(AuthenticationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAuthenticationException(AuthenticationException e) {
        log.warn("Authentication exception: {}", e.getMessage());
        ErrorResponse response = buildErrorResponse(e.getErrorCode(), e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response));
    }

    @ExceptionHandler(AuthorizationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleAuthorizationException(AuthorizationException e) {
        log.warn("Authorization exception: {}", e.getMessage());
        ErrorResponse response = buildErrorResponse(e.getErrorCode(), e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(response));
    }

    @ExceptionHandler(BadRequestException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBadRequestException(BadRequestException e) {
        log.warn("Bad request exception: {}", e.getMessage());
        ErrorResponse response = buildErrorResponse(e.getErrorCode(), e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response));
    }

    @ExceptionHandler(ConflictException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConflictException(ConflictException e) {
        log.warn("Conflict exception: {}", e.getMessage());
        ErrorResponse response = buildErrorResponse(e.getErrorCode(), e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(response));
    }

    @ExceptionHandler(ReplayDetectedException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleReplayDetectedException(ReplayDetectedException e) {
        log.warn("Replay detected exception: {}", e.getMessage());
        ErrorResponse response = buildErrorResponse(e.getErrorCode(), e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(response));
    }

    @ExceptionHandler(RateLimitException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleRateLimitException(RateLimitException e) {
        log.warn("Rate limit exception: {}", e.getMessage());
        ErrorResponse response = buildErrorResponse(e.getErrorCode(), e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(response));
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleServiceUnavailableException(ServiceUnavailableException e) {
        log.warn("Service unavailable exception: {}", e.getMessage());
        ErrorResponse response = buildErrorResponse(e.getErrorCode(), e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }

    @ExceptionHandler(TimeoutException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleTimeoutException(TimeoutException e) {
        log.warn("Timeout exception: {}", e.getMessage());
        ErrorResponse response = buildErrorResponse(e.getErrorCode(), e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(response));
    }

    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBusinessException(BusinessException e) {
        log.warn("Business exception: code={}, message={}", e.getCode(), e.getMessage());
        ErrorResponse response = buildErrorResponse("AGENT_INTERNAL_ERROR", e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleValidationException(WebExchangeBindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Validation exception: {}", message);
        ErrorResponse response = buildErrorResponse("AGENT_INVALID_PARAM", message);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response));
    }

    @ExceptionHandler(BindException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("Bind exception: {}", message);
        ErrorResponse response = buildErrorResponse("AGENT_INVALID_PARAM", message);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleConstraintViolationException(ConstraintViolationException e) {
        log.warn("Constraint violation: {}", e.getMessage());
        ErrorResponse response = buildErrorResponse("AGENT_INVALID_PARAM", e.getMessage());
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<ErrorResponse>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("Illegal argument: {}", e.getMessage());
        String message = e.getMessage() != null ? e.getMessage() : "参数错误";
        ErrorResponse response = buildErrorResponse("AGENT_INVALID_PARAM", message);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ErrorResponse>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        ErrorResponse response = buildErrorResponse("AGENT_INTERNAL_ERROR", "服务器内部错误");
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response));
    }

    private ErrorResponse buildErrorResponse(String errorCode, String message) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .timestamp(LocalDateTime.now())
                .traceId(MDC.get("traceId"))
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorResponse {
        private String errorCode;
        private String message;
        private LocalDateTime timestamp;
        private String traceId;
    }
}
