package com.iflytek.astron.console.toolkit.entity.tool;

import lombok.Data;

/**
 * Specification of a single field required by an RPA platform.
 * <p>
 * Used to define the field metadata such as display name, request key, description, type, and
 * whether it is mandatory.
 * </p>
 */
@Data
public class PlatformFieldSpec {

    /**
     * Display name shown on the UI.
     */
    private String key;

    /**
     * Request key aligned with the key in the frontend {@code fields}.
     */
    private String name;

    /**
     * Field description or remarks.
     */
    private String desc;

    /**
     * Field data type.
     * <p>
     * Currently supports "string", "number", "bool", etc. (reserved for extension).
     * </p>
     */
    private String type;

    /**
     * Whether this field is required.
     * <p>
     * {@code true} means the field must be provided, {@code false} otherwise.
     * </p>
     */
    private boolean required;
}
