package com.campushare.agent.exception;

import com.campushare.common.exception.BusinessException;
import lombok.Getter;

@Getter
public class LlmApiException extends BusinessException {

    private final int httpStatus;

    public LlmApiException(int httpStatus, String message) {
        super(5000 + httpStatus, "LLM API error: " + message);
        this.httpStatus = httpStatus;
    }
}
