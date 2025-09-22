package com.iflytek.astra.console.toolkit.sse;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.astra.console.commons.entity.workflow.Workflow;
import com.iflytek.astra.console.commons.util.SseEmitterUtil;
import com.iflytek.astra.console.toolkit.common.constant.WorkflowConst;
import com.iflytek.astra.console.toolkit.entity.core.workflow.sse.ChatResponse;
import com.iflytek.astra.console.toolkit.entity.core.workflow.sse.Choice;
import com.iflytek.astra.console.toolkit.entity.core.workflow.sse.Node;
import com.iflytek.astra.console.toolkit.mapper.workflow.WorkflowMapper;
import com.iflytek.astra.console.toolkit.service.extra.CoreSystemService;
import com.iflytek.astra.console.toolkit.util.JacksonUtil;
import com.iflytek.astra.console.toolkit.util.SpringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.net.SocketTimeoutException;
import java.util.*;

@Slf4j
@Getter
public class WorkflowSseEventSourceListener extends EventSourceListener {
    WorkflowMapper workflowMapper = SpringUtils.getBean(WorkflowMapper.class);
    CoreSystemService coreSystemService = SpringUtils.getBean(CoreSystemService.class);
    private static final ObjectMapper UTF8_MAPPER = new ObjectMapper();

    static {
        UTF8_MAPPER.getFactory().configure(JsonGenerator.Feature.ESCAPE_NON_ASCII, false);
    }


    public static final String STOP = "stop";

    /**
     * 1 : Direct output 2 : Typewriter mode
     */
    public static final int[] outputTypeEnum = new int[]{1, 2};

    String sseId;
    String flowId;
    boolean promptDebugger;
    int outputType = 1;
    long sessionStartTime;
    String version;

    // Message ordering related
    LinkedList<String> nodeIdQueue = new LinkedList<>();
    Map<String, Queue<ChatResponse>> nodeToMsgQueueMap = new HashMap<>();
    Map<String, String> nodeFinishedMap = new HashMap<>();

    public WorkflowSseEventSourceListener(String sseId) {
        this.sseId = sseId;
    }

    public WorkflowSseEventSourceListener(String flowId, String sseId, int outputType, boolean promptDebugger, String version) {
        if (!ArrayUtil.contains(outputTypeEnum, outputType)) {
            throw new IllegalArgumentException("unsupported outputType " + outputType);
        }
        this.flowId = flowId;
        this.sseId = sseId;
        this.outputType = outputType;
        this.promptDebugger = promptDebugger;
        this.version = version;
    }

    @Override
    public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
        log.info("WorkflowSseEventSourceListener[{}] onOpen, response = {}", sseId, response);
        sessionStartTime = System.currentTimeMillis();
        SseEmitterUtil.EVENTSOURCE_MAP.put(sseId, eventSource);
    }

    @Override
    public void onEvent(@NotNull EventSource eventSource, String id, String type, @NotNull String data) {
        log.info("WorkflowSseEventSourceListener[{}] onEvent data = {}", sseId, data);
        ChatResponse chatResponse = JacksonUtil.parseObject(data, ChatResponse.class);
        if (!promptDebugger) {
            if (chatResponse.getCode() != 0) {
                workflowMapper.update(Wrappers.lambdaUpdate(Workflow.class)
                        .eq(Workflow::getFlowId, flowId)
                        .set(Workflow::getCanPublish, false));
            } else {
                if (Objects.equals(version, "") || version == null) {
                    // Mark workflow as publishable
                    workflowMapper.update(Wrappers.lambdaUpdate(Workflow.class)
                            .eq(Workflow::getFlowId, flowId)
                            .set(Workflow::getCanPublish, true));
                } else {
                    // Mark workflow as non-publishable
                    workflowMapper.update(Wrappers.lambdaUpdate(Workflow.class)
                            .eq(Workflow::getFlowId, flowId)
                            .set(Workflow::getCanPublish, false));
                }
            }
        } else {
            // Check if this is the last frame
            if (chatResponse.getWorkflowStep() != null) {
                Node node = chatResponse.getWorkflowStep().getNode();
                Choice choice = chatResponse.getChoices().get(0);
                if (node.getId().equalsIgnoreCase(WorkflowConst.NodeType.FLOW_END) &&
                        choice.getFinishReason() != null &&
                        choice.getFinishReason().toString().equalsIgnoreCase(STOP)) {
                    // Delete comparison groups
                    coreSystemService.deleteComparisons(flowId, version);
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
                throw new IllegalArgumentException("unsupported outputType " + outputType);
        }
    }

    private void sendOrderedMessage(ChatResponse chatResponse) {
        Choice choice = chatResponse.getChoices().get(0);
        Node node = chatResponse.getWorkflowStep().getNode();
        String nodeId = node.getId();
        if (StringUtils.startsWithAny(nodeId, WorkflowConst.NodeType.MESSAGE, WorkflowConst.NodeType.END)) {
            nodeFinishedMap.put(nodeId, node.getFinishReason());

            if (nodeToMsgQueueMap.containsKey(nodeId)) {
                nodeToMsgQueueMap.get(nodeId).add(chatResponse);
            }
            // Message queue already exists for this node id
            else {
                nodeIdQueue.add(nodeId);
                LinkedList<ChatResponse> queue = new LinkedList<>();
                queue.add(chatResponse);
                nodeToMsgQueueMap.put(nodeId, queue);
            }

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
        }
        // Non-message nodes and non-end nodes
        else {
            chatResponse.setOrderedMsg(choice.getDelta().getContent());
            SseEmitterUtil.sendMessage(sseId, chatResponse);
        }

        if (STOP.equals(choice.getFinishReason())) {
            // Clear all unsent ordered messages
            nodeIdQueue.forEach(n -> {
                nodeToMsgQueueMap.get(n).forEach(q -> {
                    ChatResponse blankFrame = new ChatResponse();
                    blankFrame.setOrderedMsg(q.getChoices().get(0).getDelta().getContent());
                    SseEmitterUtil.sendMessage(sseId, blankFrame);
                });
            });
        }

    }

    @Override
    public void onFailure(@NotNull EventSource eventSource, Throwable t, Response response) {
        log.error("WorkflowSseEventSourceListener[{}] onFailure, response = {}, t = {}", sseId, response, t.getMessage(), t);
        String errorMsg = (t instanceof SocketTimeoutException)
                ? "Request timeout, please try again later"
                : "Connection failed, please try again later";
        ChatResponse errorResponse = new ChatResponse(errorMsg);
        SseEmitterUtil.sendAndCompleteWithError(sseId, errorResponse);
    }

    private void sendFrameLikeTypeWriter(ChatResponse chatResponse, long interval) {
        if (StrUtil.startWithAny(chatResponse.getWorkflowStep().getNode().getId(), WorkflowConst.NodeType.MESSAGE, WorkflowConst.NodeType.END)) {
            String content = chatResponse.getChoices().get(0).getDelta().getContent();
            if (StrUtil.isEmpty(content)) {
                SseEmitterUtil.sendMessage(sseId, chatResponse);
            } else {
                try {
                    // Character-by-character output, regardless of whether string contains English letters
                    for (int j = 0; j < content.length(); j++) {
                        // Send each character individually via SSE
                        ChatResponse oneWordResponse = new ChatResponse();
                        BeanUtil.copyProperties(chatResponse, oneWordResponse);
                        oneWordResponse.getChoices().get(0).getDelta().setContent(String.valueOf(content.charAt(j)));
                        // SseEmitterUtil.sendMessage(sseId, JacksonUtil.toJSONString(oneWordResponse));
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
                        } else {
                            if (interval > 0) {
                                ThreadUtil.sleep(interval);
                            }
                        }
                    }
                } catch (Exception e) {
                    // Close WebSocket
                    if (e instanceof IllegalStateException) {
                        log.error("Expired content to send, SSE already closed");
                    } else {
                        log.error("SSE sending exception", e);
                    }
                }
            }
        } else {
            SseEmitterUtil.sendMessage(sseId, chatResponse);
        }
    }
}
