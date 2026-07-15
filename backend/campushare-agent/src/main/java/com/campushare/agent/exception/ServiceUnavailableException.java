package com.campushare.agent.exception;

import com.campushare.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class ServiceUnavailableException extends BusinessException {

    private final String errorCode;

    public ServiceUnavailableException(String message) {
        super(503, message);
        this.errorCode = "AGENT_SERVICE_UNAVAILABLE";
    }
}
