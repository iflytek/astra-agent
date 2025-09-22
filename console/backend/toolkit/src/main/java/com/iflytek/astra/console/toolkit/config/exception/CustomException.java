package com.iflytek.astra.console.toolkit.config.exception;

import com.iflytek.astra.console.toolkit.common.CustomExceptionCode;
import lombok.Getter;

/**
 * @program:
 * @description: Custom exception
 * @author: xywang73
 * @create: 2020-09-14 19:55
 */
@Getter
public class CustomException extends RuntimeException {
    /**
     * Exception code
     */
    private Integer code;

    /**
     * Additional data
     */
    private Object data;

    public void setCode(Integer code) {
        this.code = code;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public CustomException(String errorMsg) {

        super(errorMsg);
        this.code = 9999;
    }

    public CustomException(CustomException ex) {

        super(ex.getMessage());
        this.code = ex.getCode();
    }

    public CustomException(CustomExceptionCode customExceptionCode) {
        super(customExceptionCode.getMessage());
        this.code = customExceptionCode.getCode();
    }

    public CustomException(String errorMsg, Integer code) {
        super(errorMsg);
        this.code = code;
    }

    public CustomException(Integer code, String errorMsg, Throwable errorCourse) {
        super(errorMsg, errorCourse);
        this.code = code;
    }

    public CustomException(String message, Integer code, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }
}
