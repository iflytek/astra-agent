package com.iflytek.astron.console.hub.service.publish;

import com.iflytek.astron.console.commons.dto.workflow.WorkflowInputsResponseDto;

/**
 * Workflow Input Service Interface
 *
 * @author Omuigix
 */
public interface WorkflowInputService {

    /**
     * Get workflow input parameter types (corresponds to original interface: getInputsType)
     *
     * @param botId Bot ID
     * @param currentUid Current user ID
     * @param spaceId Space ID
     * @return Workflow input parameter types
     */
    WorkflowInputsResponseDto getInputsType(Integer botId, String currentUid, Long spaceId);
}
