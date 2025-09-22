package com.iflytek.astra.console.commons.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.iflytek.astra.console.commons.constant.ResponseEnum;
import com.iflytek.astra.console.commons.exception.BusinessException;
import com.iflytek.astra.console.commons.util.I18nUtil;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ApiResult<T>(int code, String message, T data, Long timestamp) {
    public static <T> ApiResult<T> of(ResponseEnum responseEnum, T data) {
        return new ApiResult<>(
                responseEnum.getCode(),
                I18nUtil.getMessage(responseEnum.getMessageKey()),
                data,
                System.currentTimeMillis());
    }

    /** Success response with data */
    public static <T> ApiResult<T> success(T data) {
        return of(ResponseEnum.SUCCESS, data);
    }

    /** Success response without data */
    public static <T> ApiResult<T> success() {
        return of(ResponseEnum.SUCCESS, null);
    }

    /** Error response */
    public static <T> ApiResult<T> error(ResponseEnum responseEnum) {
        return new ApiResult<>(
                responseEnum.getCode(),
                I18nUtil.getMessage(responseEnum.getMessageKey()),
                null,
                System.currentTimeMillis());
    }

    public static <T> ApiResult<T> error(BusinessException e) {
        return new ApiResult<>(
                e.getCode(), I18nUtil.getMessage(e.getMessage()), null, System.currentTimeMillis());
    }

    public static <T> ApiResult<T> error(int code, String messageKey) {
        return new ApiResult<>(code, I18nUtil.getMessage(messageKey), null, System.currentTimeMillis());
    }
}
