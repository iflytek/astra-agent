package com.iflytek.astra.console.toolkit.entity.botConfigProtocol;

import lombok.Data;

import java.io.Serializable;

@Data
public class ModelConfig implements Serializable {
    /**
     * Persona information
     */
    String instruct;
    /**
     * Planned model
     */
    ModelProperty plan;
    /**
     * Summary model
     */
    ModelProperty summary;
}
