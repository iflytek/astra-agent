package com.iflytek.astra.console.commons.entity.bot;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class PromptBotDetail extends BotDetail {
    private List<Integer> supportUploadList;
    private List<ChatBotPromptStruct> promptStructList;
    private List<String> inputExampleList;
    private List<DatasetInfo> datasetList;
    private List<DatasetInfo> maasDatasetList;
    private Boolean editable;
    private List<Integer> releaseType;
}
