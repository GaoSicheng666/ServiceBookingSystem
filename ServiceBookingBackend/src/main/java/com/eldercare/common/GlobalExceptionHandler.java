package com.eldercare.common;

import org.springframework.http.HttpStatus;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 全局异常处理:把各类异常统一成 ApiResponse 返回,避免把堆栈暴露给前端。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常 -> 用其自带错误码。 */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        return ApiResponse.error(e.getCode(), e.getMessage());
    }

    /** 参数校验失败 -> 返回第一条校验错误信息。 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = "参数错误";
        FieldError fe = e.getBindingResult().getFieldError();
        if (fe != null) {
            msg = fe.getDefaultMessage();
        }
        return ApiResponse.error(400, msg);
    }

    /** 兜底:未预期异常 -> 500,不泄露细节。 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleOther(Exception e) {
        // 服务器端仍打印,便于排查
        e.printStackTrace();
        return ApiResponse.error(500, "服务器内部错误");
    }
}
