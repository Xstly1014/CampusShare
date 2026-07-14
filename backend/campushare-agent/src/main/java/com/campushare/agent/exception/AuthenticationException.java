package com.campushare.agent.exception;

import com.campushare.common.exception.BusinessException;

public class AuthenticationException extends BusinessException {
    public AuthenticationException(String message) {
        super(401, message);
    }
}