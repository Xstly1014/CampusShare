package com.campushare.common.exception;

import com.campushare.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常类
 */
@Getter
public class BusinessException extends RuntimeException {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 错误码
     */
    private final int code;
    
    /**
     * 错误消息
     */
    private final String errorMessage;
    
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.errorMessage = resultCode.getMessage();
    }
    
    public BusinessException(ResultCode resultCode, String customMessage) {
        super(customMessage);
        this.code = resultCode.getCode();
        this.errorMessage = customMessage;
    }
    
    public BusinessException(int code, String errorMessage) {
        super(errorMessage);
        this.code = code;
        this.errorMessage = errorMessage;
    }
    
    public BusinessException(int code, String errorMessage, Throwable cause) {
        super(errorMessage, cause);
        this.code = code;
        this.errorMessage = errorMessage;
    }
}