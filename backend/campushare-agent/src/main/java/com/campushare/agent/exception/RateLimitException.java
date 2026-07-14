package com.campushare.agent.exception;

import com.campushare.common.exception.BusinessException;

public class RateLimitException extends BusinessException {
    public RateLimitException(String message) {
        super(429, message);
    }

    public RateLimitException(int code, String message) {
        super(code, message);
    }
}