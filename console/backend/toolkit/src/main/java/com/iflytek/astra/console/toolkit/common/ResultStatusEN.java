package com.iflytek.astra.console.toolkit.common;

import lombok.Getter;
import lombok.ToString;

/**
 * @program: AICloud-Customer-Service-Robot
 * @description: Unified return status enum codes
 * @author: xywang73
 * @create: 2020-10-23 14:25
 */
@Getter
@ToString
public enum ResultStatusEN {
    SUCCESS(0, "Operation Success"),
    INTERNAL_SERVER_ERROR(-1, "Server Error"),
    BAD_REQUEST(-2, "Request Parameter Error"),
    USER_AUTH_FAILED(-3, "Authentication failed"),
    FAILED_CLOUD_ID(-4, "Failed to obtain cloudId"),
    FAILED_MCP_REG(-5, "MCP registration failed"),
    NON_SERVICE_FAIL(-6, "Non-business exception"),
    EXCEED_AUTHORITY(-7, "Unauthorized operation"),
    UNSUPPORTED_OPERATION(-8, "Unsupported operation"),
    DATA_NOT_EXIST(-9, "Data does not exist"),
    FAILED_TOOL_CALL(-10, "Tool debugging failed"),
    FAILED_MCP_GET_DETAIL(-11, "Failed to get MCP tool details"),
    FAILED_AUTH(-12, "Authorization failed!"),
    FAILED_GENERATE_SERVER_URL(-13, "Failed to generate server URL"),
    CHANNEL_DOMAIN_CANNOT_NULL_BOTH(-14, "channel and domain cannot both be null"),
    CHANNEL_CANNOT_NULL(-15, "channel cannot be null"),
    PATCH_ID_CANNOT_NULL(-16, "patchId cannot be null"),
    FLOW_PROTOCOL_EMPTY(-17, "Data does not exist"),
    FLOW_ANS_MODE_ILLEGAL(-18, "Invalid flowAnsMode"),
    NOT_BE_EMPTY(-19, "Cannot be empty"),
    UNSUPPORTED_FILE_FORMAT(-20, "Unsupported file format"),
    FAILED_GET_FILE_TYPE(-21, "Failed to get file type"),
    VERSION_EXISTED(-22, "Version already exists"),
    INVALID_TYPE(-23, "Invalid type"),
    FAILED_EXPORT(-24, "Export failed"),
    NOT_CUSTOM_MODEL(-25, "Not a custom model"),
    DELIMITER_SAME(-26, "Duplicate delimiter exists"),
    APPID_CANNOT_EMPTY(-27, "appId cannot be empty"),
    NOT_GET_UID(-28, "uid not obtained"),
    FAILED_GET_TRACE(-29, "Failed to get trace log"),
    OPERATION_FAILED(-30, "Operation failed, please try again later"),
    INFO_MISS(-31, "Missing information"),
    APPID_MISS(-32, "AppId missing"),
    ID_EMPTY(-33, "ID is empty"),
    PARAM_MISS(-34, "Missing parameter"),
    NO_INPUT_ANY_DATA(-35, "No data provided"),
    PARAM_ERROR(-36, "Parameter error"),
    STREAM_PROTOCOL_EMPTY(-37, "Stream protocol empty"),
    FILTER_CONF_MISS(-38, "Missing filter configuration"),
    PROTOCOL_EMPTY(-39, "No protocol"),
    FILE_EMPTY(-40, "File is empty"),
    FILE_EXTENSION(-41, "Invalid file extension"),
    FILE_UPLOAD_FAILED(-42, "File upload failed"),
    UPLOADED_BUSINESS_NOT_SUPPORT(-43, "Current upload business not supported"),
    PASSWORD_ERROR(-44, "Password incorrect"),
    ;

    /**
     * Business exception code
     */
    private final Integer code;
    /**
     * Business exception message description
     */
    private final String message;

    ResultStatusEN(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
