package com.iflytek.astron.console.toolkit.entity.tool;

import java.util.Map;

/**
 * Request object for updating an existing RPA assistant.
 * <p>
 * This record carries the update information such as the assistant name,
 * status, configuration fields, and whether the fields should be fully replaced.
 * </p>
 *
 * @param assistantName  New name of the assistant (optional; must be unique per user if provided)
 * @param status         New status of the assistant (e.g., enabled/disabled); nullable if not updating
 * @param fields         Key-value pairs of updated credential/parameter fields
 * @param replaceFields  Whether to replace all fields with the new set
 *                       ({@code true} = replace all, {@code false} = merge update)
 */
public record UpdateRpaAssistantReq(
        String assistantName,
        Integer status,
        Map<String, String> fields,
        Boolean replaceFields
) {}
