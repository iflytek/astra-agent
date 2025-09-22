package com.iflytek.astra.console.toolkit.common;

import lombok.*;

/**
 * @program: AICloud-Customer-Service-Robot
 * @description: Unified return status enum codes
 * @author: xywang73
 * @create: 2020-10-23 14:25
 */
@Getter
@ToString
public enum ResultStatus {
    SUCCESS(0, "操作成功"),
    INTERNAL_SERVER_ERROR(-1, "服务端异常"),
    BAD_REQUEST(-2, "请求参数错误"),
    USER_AUTH_FAILED(-3, "登陆鉴权未通过"),
    FAILED_CLOUD_ID(-4, "获取cloudId失败"),
    FAILED_MCP_REG(-5, "MCP注册失败"),
    NON_SERVICE_FAIL(-6, "非业务异常"),
    EXCEED_AUTHORITY(-7, "越权操作"),
    UNSUPPORTED_OPERATION(-8, "不支持的操作"),
    DATA_NOT_EXIST(-9, "数据不存在"),
    FAILED_TOOL_CALL(-10, "工具调试失败"),
    FAILED_MCP_GET_DETAIL(-11, "获取MCP工具详情失败"),
    FAILED_AUTH(-12, "授权失败！"),
    FAILED_GENERATE_SERVER_URL(-13, "生成server url 失败"),
    CHANNEL_DOMAIN_CANNOT_NULL_BOTH(-14, "channel和domain不能同时为null"),
    CHANNEL_CANNOT_NULL(-15, "channel不能为空"),
    PATCH_ID_CANNOT_NULL(-16, "patchId不能为空"),
    FLOW_PROTOCOL_EMPTY(-17, "数据不存在"),
    FLOW_ANS_MODE_ILLEGAL(-18, "非法的flowAnsMode"),
    NOT_BE_EMPTY(-19, "不能为空"),
    UNSUPPORTED_FILE_FORMAT(-20, "不支持的文件格式"),
    FAILED_GET_FILE_TYPE(-21, "获取文件类型失败"),
    VERSION_EXISTED(-22, "版本已存在"),
    INVALID_TYPE(-23, "无效类型"),
    FAILED_EXPORT(-24, "导出失败"),
    NOT_CUSTOM_MODEL(-25, "非自定义模型"),
    DELIMITER_SAME(-26, "存在重复分隔符"),
    APPID_CANNOT_EMPTY(-27, "appId不能为空"),
    NOT_GET_UID(-28, "未获取到uid"),
    FAILED_GET_TRACE(-29, "Trace日志获取失败"),
    OPERATION_FAILED(-30, "操作失败，请稍后再试"),
    INFO_MISS(-31, "信息缺失"),
    APPID_MISS(-32, "AppId缺失"),
    ID_EMPTY(-33, "id为空"),
    PARAM_MISS(-34, "缺少参数"),
    NO_INPUT_ANY_DATA(-35, "未传入任何数据"),
    PARAM_ERROR(-36, "参数错误"),
    STREAM_PROTOCOL_EMPTY(-37, "流协议空"),
    FILTER_CONF_MISS(-38, "缺少过滤器配置"),
    PROTOCOL_EMPTY(-39, "没有协议"),
    FILE_EMPTY(-40, "文件为空"),
    FILE_EXTENSION(-41, "文件名后缀校验非法"),
    FILE_UPLOAD_FAILED(-42, "上传文件失败"),
    UPLOADED_BUSINESS_NOT_SUPPORT(-43, "不支持当前上传业务"),
    PASSWORD_ERROR(-44, "密码错误"),
    ;

    /**
     * Business exception code
     */
    private final Integer code;
    /**
     * Business exception message description
     */
    private final String message;

    ResultStatus(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
