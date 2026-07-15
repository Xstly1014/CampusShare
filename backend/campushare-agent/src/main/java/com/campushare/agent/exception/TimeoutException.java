package com.campushare.agent.exception;

import com.campushare.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class TimeoutException extends BusinessException {

    private final String errorCode;

    public TimeoutException(String message) {
        super(504, message);
        this.errorCode = "AGENT_TIMEOUT";
    }
}
