package com.iflytek.astron.console.toolkit.common;


import com.iflytek.astron.console.toolkit.handler.language.LanguageContext;
import com.iflytek.astron.console.toolkit.tool.CommonTool;
import lombok.Data;

/**
 * @program: AICloud-Customer-Service-Robot
 * @description: Unified return entity
 * @author: xywang73
 * @create: 2020-10-23 14:39
 */
@Data
public class Result<T> {

    String sid;
    /**
     * Business error code
     */
    Integer code;
    /**
     * Message description
     */
    String message;
    /**
     * Return parameters
     */
    T data;

    protected Result() {}

    protected Result(ResultStatus resultStatus, T data) {
        this.code = resultStatus.getCode();
        this.message = resultStatus.getMessage();
        this.data = data;
        this.sid = CommonTool.genSid();
    }

    protected Result(ResultStatusEN resultStatus, T data) {
        this.code = resultStatus.getCode();
        this.message = resultStatus.getMessage();
        this.data = data;
        this.sid = CommonTool.genSid();
    }


    protected Result(int code, String message) {
        this.code = code;
        this.message = message;
        this.sid = CommonTool.genSid();
    }

    protected Result(int code, String message, String sid) {
        this.code = code;
        this.message = message;
        this.sid = sid;
    }

    protected Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    protected Result(int code, String message, T data, String sid) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.sid = sid;
    }

    /**
     * Select enum by language and create Result
     *
     * @param zhStatus Chinese enum
     * @param enStatus English enum
     * @param data Return data
     */
    private static <T> Result<T> from(ResultStatus zhStatus, ResultStatusEN enStatus, T data) {
        if (LanguageContext.isEn()) {
            return new Result<>(enStatus, data);
        } else {
            return new Result<>(zhStatus, data);
        }
    }

    /**
     * Business success returns business code and description
     */
    public static Result<Void> success() {
        return from(ResultStatus.SUCCESS, ResultStatusEN.SUCCESS, null);
    }

    /**
     * Business success returns business code, description and return parameters
     */
    public static <T> Result<T> success(T data) {
        return from(ResultStatus.SUCCESS, ResultStatusEN.SUCCESS, data);
    }

    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultStatus.SUCCESS.getCode(), message, data);
    }

    /**
     * Business success returns business code, description and return parameters
     */
    public static <T> Result<T> success(ResultStatus zhStatus, T data) {
        if (zhStatus == null) {
            return success(data);
        }
        ResultStatusEN enStatus = ResultStatusEN.valueOf(zhStatus.name());
        return from(zhStatus, enStatus, data);
    }

    /**
     * Business exception returns business code and description
     */
    public static <T> Result<T> failure() {
        return from(ResultStatus.INTERNAL_SERVER_ERROR, ResultStatusEN.INTERNAL_SERVER_ERROR, null);
    }


    /**
     * Business exception returns business code, description and return parameters
     */
    public static <T> Result<T> failure(ResultStatus resultStatus) {
        return failure(resultStatus, null);
    }

    public static <T> Result<T> failure(String message) {
        return new Result<>(-1, message);
    }

    public static <T> Result<T> failure(int code, String message) {
        return new Result<>(code, message);
    }

    public static <T> Result<T> failure(int code, String message, T data) {
        return new Result<>(code, message, data);
    }

    public static <T> Result<T> failure(int code, String message, T data, String sid) {
        return new Result<>(code, message, data, sid);
    }

    /**
     * Business exception returns business code, description and return parameters
     */
    public static <T> Result<T> failure(ResultStatus zhStatus, T data) {
        if (zhStatus == null) {
            return failure();
        }
        ResultStatusEN enStatus = ResultStatusEN.valueOf(zhStatus.name());
        return from(zhStatus, enStatus, data);
    }


    public static <T> Result<T> failure(String message, String sid) {
        return new Result<>(-1, message, sid);
    }

    public boolean noError() {
        return this.code == 0;
    }

    public boolean hasError() {
        return !noError();
    }

}
