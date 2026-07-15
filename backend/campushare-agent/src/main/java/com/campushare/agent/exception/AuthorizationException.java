package com.campushare.agent.exception;

import com.campushare.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class AuthorizationException extends BusinessException {

    private final String errorCode;

    public AuthorizationException(String message) {
        super(403, message);
        this.errorCode = "AGENT_FORBIDDEN";
    }
}
