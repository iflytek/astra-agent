package com.iflytek.astra.console.hub.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author yingpeng
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class LongFileDto {

    private String chatId;

    private String fileId;

    private String linkId;

    private String fileBusinessKey;

    /**
     * File parameter name of the agent's start node
     */
    private String paramName;
}
