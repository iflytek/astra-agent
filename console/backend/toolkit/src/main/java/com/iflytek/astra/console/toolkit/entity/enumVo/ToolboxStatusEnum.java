package com.iflytek.astra.console.toolkit.entity.enumVo;


/**
 * Tool status enumeration
 */
public enum ToolboxStatusEnum {

    DRAFT(0),
    FORMAL(1);

    private final Integer code;

    ToolboxStatusEnum(Integer code) {
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }

    public static ToolboxStatusEnum getByCode(Integer status) {
        for (ToolboxStatusEnum value : ToolboxStatusEnum.values()) {
            if (value.ordinal() == status) {
                return value;
            }
        }
        throw new EnumConstantNotPresentException(ToolboxStatusEnum.class, "Related enumeration class not found");
    }
}
