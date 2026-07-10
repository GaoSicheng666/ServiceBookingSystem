package com.eldercare.common;

/** 业务异常:携带错误码,由全局处理器转成统一响应。 */
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        this(1, message);
    }

    public int getCode() {
        return code;
    }
}
