package com.campushare.agent.exception;

import com.campushare.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class ConflictException extends BusinessException {

    private final String errorCode;

    public ConflictException(String message) {
        super(409, message);
        this.errorCode = "AGENT_REPLAY_DETECTED";
    }
}
