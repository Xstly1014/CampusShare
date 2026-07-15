package com.campushare.agent.exception;

import com.campushare.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class RateLimitException extends BusinessException {

    private final String errorCode;

    public RateLimitException(String message) {
        super(429, message);
        this.errorCode = "AGENT_RATE_LIMIT_USER";
    }

    public RateLimitException(int code, String message) {
        super(code, message);
        this.errorCode = "AGENT_RATE_LIMIT_USER";
    }

    public RateLimitException(String errorCode, String message) {
        super(429, message);
        this.errorCode = errorCode;
    }

    public static RateLimitException global(String msg) {
        return new RateLimitException("AGENT_RATE_LIMIT_GLOBAL", msg);
    }

    public static RateLimitException user(String msg) {
        return new RateLimitException("AGENT_RATE_LIMIT_USER", msg);
    }

    public static RateLimitException session(String msg) {
        return new RateLimitException("AGENT_RATE_LIMIT_SESSION", msg);
    }

    public static RateLimitException ip(String msg) {
        return new RateLimitException("AGENT_RATE_LIMIT_IP", msg);
    }
}
