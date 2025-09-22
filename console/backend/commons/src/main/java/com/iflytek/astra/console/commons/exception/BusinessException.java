package com.iflytek.astra.console.commons.exception;

import com.iflytek.astra.console.commons.constant.ResponseEnum;

/** Business exception */
public class BusinessException extends RuntimeException {

    private final int code;
    private final ResponseEnum responseEnum;
    private final Object[] args;

    public BusinessException(ResponseEnum responseEnum) {
        super(responseEnum.getMessageKey());
        this.code = responseEnum.getCode();
        this.responseEnum = responseEnum;
        this.args = new Object[0];
    }

    public BusinessException(ResponseEnum responseEnum, Object... args) {
        super(formatMessage(responseEnum.getMessageKey(), args));
        this.code = responseEnum.getCode();
        this.responseEnum = responseEnum;
        this.args = args != null ? args : new Object[0];
    }

    public int getCode() {
        return code;
    }

    public ResponseEnum getResponseEnum() {
        return responseEnum;
    }

    public Object[] getArgs() {
        return args;
    }

    private static String formatMessage(String template, Object... args) {
        if (args == null || args.length == 0) {
            return template;
        }
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template + " " + String.join(", ",
                            java.util.Arrays.stream(args).map(String::valueOf).toList());
        }
    }
}
