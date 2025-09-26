package com.iflytek.astron.console.commons.entity.bot;

import com.iflytek.astron.console.commons.dto.bot.BotModelDto;
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
    private BotModelDto botModel;
}
