package com.iflytek.astron.console.hub.service.workflow;


import com.iflytek.astron.console.commons.entity.bot.BotInfoDto;
import com.iflytek.astron.console.commons.entity.workflow.CloneSynchronize;
import com.iflytek.astron.console.hub.entity.maas.MaasDuplicate;
import com.iflytek.astron.console.hub.entity.maas.MaasTemplate;
import com.iflytek.astron.console.hub.entity.maas.WorkflowTemplateQueryDto;

import java.util.List;


public interface BotMaasService {
    BotInfoDto createFromTemplate(String uid, MaasDuplicate massDuplicate);

    Integer massCopySynchronize(CloneSynchronize synchronize);

    List<MaasTemplate> templateList(WorkflowTemplateQueryDto queryDto);
}
