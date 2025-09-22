package com.iflytek.astra.console.toolkit.entity.es.agentBuilder;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.Data;

import java.util.List;

@Data
public class Trace {
    String id;
    Integer duration;
    @JSONField(name = "next_log_ids")
    List<Object> next_log_ids;
    @JSONField(name = "start_time")
    Long startTime;
    @JSONField(name = "node_type")
    String nodeType;
    TraceData data;
    @JSONField(name = "first_frame_duration")
    Integer firstFrameDuration;
    @JSONField(name = "node_name")
    String nodeName;
    @JSONField(name = "end_time")
    Long endTime;
    @JSONField(name = "running_status")
    Boolean runningStatus;
    String sid;
    @JSONField(name = "node_id")
    String nodeId;

    String expectedAnswer;
}
