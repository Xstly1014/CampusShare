package com.campushare.agent.exception;

import com.campushare.common.exception.BusinessException;

public class ReplayDetectedException extends BusinessException {
    public ReplayDetectedException(String message) {
        super(409, message);
    }
}