
package com.iflytek.astra.console.toolkit.entity.dto;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowDsl {
    /**
     * Workflow basic information
     */
    private Map<String, Object> flowMeta;
    /**
     * Workflow core protocol
     */
    private Map<String, Object> flowData;
}
