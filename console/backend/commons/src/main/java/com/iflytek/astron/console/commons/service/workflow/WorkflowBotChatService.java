package com.iflytek.astron.console.commons.service.workflow;

import com.iflytek.astron.console.commons.entity.bot.ChatBotReqDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface WorkflowBotChatService {

    void chatWorkflowBot(ChatBotReqDto chatBotReqDto, SseEmitter sseEmitter, String sseId, String workflowOperation, String workflowVersion);
}
