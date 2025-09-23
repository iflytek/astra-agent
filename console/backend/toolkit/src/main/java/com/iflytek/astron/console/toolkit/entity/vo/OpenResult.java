package com.iflytek.astron.console.toolkit.entity.vo;


import com.iflytek.astron.console.toolkit.handler.SidManagerHandler;
import lombok.Data;

@Data
public class OpenResult<T> {
    private Integer code;
    private String message;
    private String sid;
    private T result;

    public OpenResult() {}

    public OpenResult(Integer code, String message, String sid, T result) {
        this.code = code;
        this.message = message;
        this.sid = sid;
        this.result = result;
    }

    public OpenResult(Integer code, String message, T result) {
        this.code = code;
        this.message = message;
        this.result = result;
    }

    public static <T> OpenResult<T> success(T data) {
        return new OpenResult<>(0, "Success", SidManagerHandler.get(), data);
    }

    public static OpenResult<Void> success() {
        return new OpenResult<>(0, "Success", SidManagerHandler.get(), null);
    }
}
