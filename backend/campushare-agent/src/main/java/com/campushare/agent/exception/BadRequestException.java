package com.campushare.agent.exception;

import com.campushare.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class BadRequestException extends BusinessException {

    private final String errorCode;

    public BadRequestException(String message) {
        super(400, message);
        this.errorCode = "AGENT_INVALID_PARAM";
    }
}
