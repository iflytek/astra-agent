package com.iflytek.astron.console.toolkit.common.constant.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ToolErrorStatus {

    AppInitErr(30001, "Initialization failed"),
    CommonErr(30100, "General error"),
    JsonProtocolParserErr(30200, "Protocol parsing failed"),
    JsonSchemaValidateErr(30201, "Protocol validation failed"),
    OpenapiSchemaValidateErr(30300, "Protocol parsing failed"),
    OpenapiSchemaBodyTypeNotSupportErr(30301, "Body type not supported"),
    OpenapiSchemaServerNotExistErr(30302, "Server does not exist"),
    ThirdApiRequestFailedErr(30400, "Third-party request failed"),
    FunctionCallFailedErr(30401, "Function call invocation failed"),
    LLMCallFailedErr(30402, "LLM invocation failed"),
    ToolNotExistErr(30500, "Tool does not exist"),
    OperationIdNotExistErr(30600, "Operation does not exist"),

    ;

    final int code;
    final String message;

    public static ToolErrorStatus find(int code) {
        for (ToolErrorStatus agentError : ToolErrorStatus.values()) {
            if (agentError.getCode() == code) {
                return agentError;
            }
        }
        return null;
    }
}
