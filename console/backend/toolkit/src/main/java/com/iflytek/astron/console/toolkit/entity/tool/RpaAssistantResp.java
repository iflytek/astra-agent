package com.iflytek.astron.console.toolkit.entity.tool;

import com.alibaba.fastjson2.JSONArray;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Response object for RPA assistant operations.
 * <p>
 * Represents the details of an RPA assistant, including basic information,
 * configuration fields, related robots, and creation time.
 * </p>
 *
 * @param id           Primary key ID of the assistant
 * @param platformId   ID of the RPA platform that the assistant belongs to
 * @param assistantName Display name of the assistant
 * @param status       Status of the assistant (e.g., enabled/disabled)
 * @param fields       Key-value map of assistant configuration fields (e.g., apiKey, secret)
 * @param robots       JSON array of robots/workflows bound to this assistant
 * @param createTime   Record creation time
 */
public record RpaAssistantResp(
        Long id,
        Long platformId,
        String assistantName,
        String remarks,
        Integer status,
        Map<String, String> fields,
        JSONArray robots,
        LocalDateTime createTime
) {}
