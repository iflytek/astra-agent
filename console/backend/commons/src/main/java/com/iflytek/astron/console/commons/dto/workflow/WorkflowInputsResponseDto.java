package com.iflytek.astron.console.commons.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工作流输入类型响应DTO 对应原接口：getInputsType的返回结果
 *
 * @author Omuigix
 */
@Data
@Schema(name = "WorkflowInputsResponseDto", description = "工作流输入类型响应")
public class WorkflowInputsResponseDto {

    @Schema(description = "输入参数列表")
    private List<InputParameter> parameters;

    /**
     * 输入参数定义
     */
    @Data
    @Schema(name = "InputParameter", description = "输入参数定义")
    public static class InputParameter {
        @Schema(description = "参数ID")
        private String id;

        @Schema(description = "参数名称")
        private String name;

        @Schema(description = "参数类型")
        private String type;

        @Schema(description = "是否必需")
        private Boolean required;

        @Schema(description = "参数描述")
        private String description;

        @Schema(description = "参数模式定义")
        private Map<String, Object> schema;

        @Schema(description = "是否禁用删除")
        private Boolean deleteDisabled;

        @Schema(description = "名称错误信息")
        private String nameErrMsg;
    }
}
