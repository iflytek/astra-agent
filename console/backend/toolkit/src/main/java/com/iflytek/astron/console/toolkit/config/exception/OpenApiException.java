package com.iflytek.astron.console.toolkit.config.exception;

public class OpenApiException extends RuntimeException {
    /**
     * Exception code
     */
    private Integer code;

    /**
     * Additional data
     */
    private Object data;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public OpenApiException(String errorMsg) {

        super(errorMsg);
        this.code = 9999;
    }

    public OpenApiException(String errorMsg, Integer code) {
        super(errorMsg);
        this.code = code;
    }

    public OpenApiException(Integer code, String errorMsg, Throwable errorCourse) {
        super(errorMsg, errorCourse);
        this.code = code;
    }

    public OpenApiException(String message, Integer code, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }
}
