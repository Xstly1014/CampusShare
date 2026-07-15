package com.campushare.agent.exception;

import com.campushare.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class AuthenticationException extends BusinessException {

    private final String errorCode;

    public AuthenticationException(String message) {
        super(401, message);
        this.errorCode = "AGENT_AUTH_FAILED";
    }
}
