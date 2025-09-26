package com.iflytek.astron.console.toolkit.sse;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.astron.console.commons.entity.workflow.Workflow;
import com.iflytek.astron.console.commons.util.SseEmitterUtil;
import com.iflytek.astron.console.toolkit.common.constant.WorkflowConst;
import com.iflytek.astron.console.toolkit.entity.core.workflow.sse.ChatResponse;
import com.iflytek.astron.console.toolkit.entity.core.workflow.sse.Choice;
import com.iflytek.astron.console.toolkit.entity.core.workflow.sse.Node;
import com.iflytek.astron.console.toolkit.mapper.workflow.WorkflowMapper;
import com.iflytek.astron.console.toolkit.service.extra.CoreSystemService;
import com.iflytek.astron.console.toolkit.util.JacksonUtil;
import com.iflytek.astron.console.toolkit.util.SpringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.SocketTimeoutException;
import java.util.*;

@Slf4j
@Getter
public class WorkflowSseEventSourceListener extends EventSourceListener {

    private volatile WorkflowMapper workflowMapper;
    private volatile CoreSystemService coreSystemService;
    private volatile long sessionStartTime;

    private static final ObjectMapper UTF8_MAPPER = new ObjectMapper();
    static {
        UTF8_MAPPER.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
    }

    public static final String STOP = "stop";

    /**
     * 1 : Direct output 2 : Typewriter mode
     */
    public static final int[] outputTypeEnum = new int[] {1, 2};

    String sseId;
    String flowId;
    boolean promptDebugger;
    int outputType = 1;
    String version;

    // Message ordering related
    final LinkedList<String> nodeIdQueue = new LinkedList<>();
    final Map<String, Queue<ChatResponse>> nodeToMsgQueueMap = new HashMap<>();
    final Map<String, String> nodeFinishedMap = new HashMap<>();

    public WorkflowSseEventSourceListener(String sseId) {
        this.sseId = sseId;
        // 不做任何 Bean 获取或 heavy 初始化，遵循“构造器不含业务逻辑”
    }

    public WorkflowSseEventSourceListener(String flowId, String sseId, int outputType, boolean promptDebugger, String version) {
        this.flowId = flowId;
        this.sseId = sseId;
        // 非法值降级，避免构造器抛出异常（CT_CONSTRUCTOR_THROW 告警来源之一）
        if (!ArrayUtil.contains(outputTypeEnum, outputType)) {
            log.warn("unsupported outputType {}, fallback to 1 (Direct output)", outputType);
            this.outputType = 1;
        } else {
            this.outputType = outputType;
        }
        this.promptDebugger = promptDebugger;
        this.version = version;
    }

    private void ensureBeans() {
        if (workflowMapper == null) {
            try {
                workflowMapper = SpringUtils.getBean(WorkflowMapper.class);
            } catch (Exception e) {
                log.error("Failed to init WorkflowMapper from Spring context.", e);
            }
        }
        if (coreSystemService == null) {
            try {
                coreSystemService = SpringUtils.getBean(CoreSystemService.class);
            } catch (Exception e) {
                log.error("Failed to init CoreSystemService from Spring context.", e);
            }
        }
    }

    @Override
    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
        ensureBeans();
        log.info("WorkflowSseEventSourceListener[{}] onOpen, response = {}", sseId, response);
        sessionStartTime = System.currentTimeMillis();
        SseEmitterUtil.EVENTSOURCE_MAP.put(sseId, eventSource);
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, String id, String type, @NotNull String data) {
        ensureBeans();
        log.info("WorkflowSseEventSourceListener[{}] onEvent data = {}", sseId, data);
        ChatResponse chatResponse = JacksonUtil.parseObject(data, ChatResponse.class);
        if (chatResponse == null) {
            log.warn("WorkflowSseEventSourceListener[{}] received null ChatResponse after parse.", sseId);
            return;
        }

        if (!promptDebugger) {
            if (chatResponse.getCode() != 0) {
                if (workflowMapper != null) {
                    workflowMapper.update(Wrappers.lambdaUpdate(Workflow.class)
                            .eq(Workflow::getFlowId, flowId)
                            .set(Workflow::getCanPublish, false));
                }
            } else {
                if (StringUtils.isBlank(version)) {
                    if (workflowMapper != null) {
                        workflowMapper.update(Wrappers.lambdaUpdate(Workflow.class)
                                .eq(Workflow::getFlowId, flowId)
                                .set(Workflow::getCanPublish, true));
                    }
                } else {
                    if (workflowMapper != null) {
                        workflowMapper.update(Wrappers.lambdaUpdate(Workflow.class)
                                .eq(Workflow::getFlowId, flowId)
                                .set(Workflow::getCanPublish, false));
                    }
                }
            }
        } else {
            // Check if this is the last frame
            if (chatResponse.getWorkflowStep() != null
                    && chatResponse.getWorkflowStep().getNode() != null
                    && chatResponse.getChoices() != null
                    && !chatResponse.getChoices().isEmpty()) {

                Node node = chatResponse.getWorkflowStep().getNode();
                Choice choice = chatResponse.getChoices().get(0);
                if (node.getId().equalsIgnoreCase(WorkflowConst.NodeType.FLOW_END)
                        && choice.getFinishReason() != null
                        && choice.getFinishReason().toString().equalsIgnoreCase(STOP)) {
                    if (coreSystemService != null) {
                        coreSystemService.deleteComparisons(flowId, version);
                    }
                }
            }
        }
        sendMessage(chatResponse);
    }

    @Override
    public void onClosed(@NotNull EventSource eventSource) {
        log.info("WorkflowSseEventSourceListener[{}] onClosed", sseId);
        SseEmitterUtil.close(sseId);
    }

    private void sendMessage(ChatResponse chatResponse) {
        chatResponse.setExecutedTime(NumberUtil.div(System.currentTimeMillis() - sessionStartTime, 1000));
        switch (outputType) {
            case 1:
                SseEmitterUtil.sendMessage(sseId, chatResponse);
                break;
            case 2:
                sendFrameLikeTypeWriter(chatResponse, 20L);
                break;
            default:
                // 理论不可达，已在构造器里做了降级
                log.warn("Unsupported outputType {}, fallback to direct output.", outputType);
                SseEmitterUtil.sendMessage(sseId, chatResponse);
        }
    }

    private void sendOrderedMessage(ChatResponse chatResponse) {
        if (chatResponse.getChoices() == null || chatResponse.getChoices().isEmpty()
                || chatResponse.getWorkflowStep() == null || chatResponse.getWorkflowStep().getNode() == null) {
            SseEmitterUtil.sendMessage(sseId, chatResponse);
            return;
        }

        Choice choice = chatResponse.getChoices().get(0);
        Node node = chatResponse.getWorkflowStep().getNode();
        String nodeId = node.getId();

        if (StringUtils.startsWithAny(nodeId, WorkflowConst.NodeType.MESSAGE, WorkflowConst.NodeType.END)) {
            nodeFinishedMap.put(nodeId, node.getFinishReason());

            nodeToMsgQueueMap.computeIfAbsent(nodeId, k -> {
                nodeIdQueue.add(k);
                return new LinkedList<>();
            }).add(chatResponse);

            String fstNodeId = nodeIdQueue.peek();
            if (fstNodeId != null) {
                ChatResponse fstCR = nodeToMsgQueueMap.get(fstNodeId).poll();
                if (fstCR != null) {
                    chatResponse.setOrderedMsg(choice.getDelta().getContent());
                }
                if (STOP.equals(nodeFinishedMap.get(fstNodeId))) {
                    nodeIdQueue.poll();
                }
            }

            SseEmitterUtil.sendMessage(sseId, chatResponse);
        } else {
            chatResponse.setOrderedMsg(choice.getDelta().getContent());
            SseEmitterUtil.sendMessage(sseId, chatResponse);
        }

        if (STOP.equals(choice.getFinishReason())) {
            // Clear all unsent ordered messages
            nodeIdQueue.forEach(n -> {
                Queue<ChatResponse> q = nodeToMsgQueueMap.get(n);
                if (q != null) {
                    q.forEach(msg -> {
                        ChatResponse blankFrame = new ChatResponse();
                        blankFrame.setOrderedMsg(msg.getChoices().get(0).getDelta().getContent());
                        SseEmitterUtil.sendMessage(sseId, blankFrame);
                    });
                }
            });
        }
    }

    @Override
    public void onFailure(@NotNull EventSource eventSource,
            @Nullable Throwable t,
            @Nullable Response response) {
        String errorMsg;
        if (t instanceof SocketTimeoutException) {
            errorMsg = "Request timeout, please try again later";
        } else {
            errorMsg = "Connection failed, please try again later";
        }

        if (t != null) {
            log.error("WorkflowSseEventSourceListener[{}] onFailure, response = {}, error = {}", sseId, response, t.getMessage(), t);
        } else {
            log.error("WorkflowSseEventSourceListener[{}] onFailure, response = {}, error = <null Throwable>", sseId, response);
        }

        ChatResponse errorResponse = new ChatResponse(errorMsg);
        SseEmitterUtil.sendAndCompleteWithError(sseId, errorResponse);
    }

    private void sendFrameLikeTypeWriter(ChatResponse chatResponse, long interval) {
        if (chatResponse.getWorkflowStep() != null
                && chatResponse.getWorkflowStep().getNode() != null
                && StrUtil.startWithAny(chatResponse.getWorkflowStep().getNode().getId(),
                        WorkflowConst.NodeType.MESSAGE, WorkflowConst.NodeType.END)) {

            String content = null;
            if (chatResponse.getChoices() != null && !chatResponse.getChoices().isEmpty()
                    && chatResponse.getChoices().get(0).getDelta() != null) {
                content = chatResponse.getChoices().get(0).getDelta().getContent();
            }

            if (StrUtil.isEmpty(content)) {
                SseEmitterUtil.sendMessage(sseId, chatResponse);
            } else {
                try {
                    for (int j = 0; j < content.length(); j++) {
                        ChatResponse oneWordResponse = new ChatResponse();
                        BeanUtil.copyProperties(chatResponse, oneWordResponse);
                        oneWordResponse.getChoices().get(0).getDelta().setContent(String.valueOf(content.charAt(j)));
                        try {
                            String json = UTF8_MAPPER.writeValueAsString(oneWordResponse);
                            SseEmitterUtil.sendMessage(sseId, json);
                        } catch (Exception e) {
                            log.error("JSON serialization failed", e);
                        }
                        char codePoint = content.charAt(j);
                        if ((codePoint >= 65 && codePoint <= 90) // A-Z
                                || (codePoint >= 97 && codePoint <= 122)) {
                            ThreadUtil.sleep(1);
                        } else if (interval > 0) {
                            ThreadUtil.sleep(interval);
                        }
                    }
                } catch (IllegalStateException e) {
                    log.error("Expired content to send, SSE already closed");
                } catch (Exception e) {
                    log.error("SSE sending exception", e);
                }
            }
        } else {
            SseEmitterUtil.sendMessage(sseId, chatResponse);
        }
    }
}
