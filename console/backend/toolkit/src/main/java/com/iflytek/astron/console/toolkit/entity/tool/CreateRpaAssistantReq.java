package com.iflytek.astron.console.toolkit.entity.tool;

import java.util.Map;

/**
 * Request object for creating an RPA assistant.
 * <p>
 * This record holds the information required when a user creates an RPA assistant,
 * including platform, name, icon, credential fields, and optional remarks.
 * </p>
 *
 * @param platformId    ID of the RPA platform (foreign key referencing {@code rpa_info.id})
 * @param assistantName Display name of the assistant
 * @param icon          Icon URL of the assistant
 * @param fields        Key-value pairs of credential/parameter fields (e.g., apiKey, secret)
 * @param remarks       Optional remarks or description
 */
public record CreateRpaAssistantReq(
        Long platformId,
        String assistantName,
        String icon,
        Map<String, String> fields,
        String remarks
) {}
