package com.iflytek.astra.console.hub.service.workflow;


import com.iflytek.astra.console.commons.entity.bot.BotInfoDto;
import com.iflytek.astra.console.commons.entity.workflow.CloneSynchronize;
import com.iflytek.astra.console.hub.entity.maas.MaasDuplicate;
import com.iflytek.astra.console.hub.entity.maas.MaasTemplate;
import com.iflytek.astra.console.hub.entity.maas.WorkflowTemplateQueryDto;

import java.util.List;


public interface BotMaasService {
    BotInfoDto createFromTemplate(String uid, MaasDuplicate massDuplicate);

    Integer massCopySynchronize(CloneSynchronize synchronize);

    List<MaasTemplate> templateList(WorkflowTemplateQueryDto queryDto);
}
