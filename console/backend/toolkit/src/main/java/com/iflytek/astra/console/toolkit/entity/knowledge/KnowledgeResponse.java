package com.iflytek.astra.console.toolkit.entity.knowledge;

import lombok.Data;

@Data
public class KnowledgeResponse {
    Integer code;
    String sid;
    String message;
    Object data;
}
