package com.iflytek.astron.console.toolkit.entity.biz.modelconfig;

import lombok.Data;

import java.util.List;

@Data
public class Model {
    CompletionParams completionParams;
    // String mode = "completion";
    String domain;
    List<String> patchId;
    String serviceId;
    Long llmId;
    Integer llmSource;
    String api;
    Long modelId;
}
