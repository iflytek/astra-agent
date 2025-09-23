package com.iflytek.astron.console.toolkit.entity.es.agentBuilder;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SparkAgentBuilder {
    String sub;
    String question;
    @JSONField(name = "first_frame_duration")
    Integer firstFrameDuration;
    @JSONField(name = "end_time")
    Long endTime;
    String type;
    @JSONField(name = "chat_id")
    String chatId;
    String sid;
    Integer duration;
    String uid;
    @JSONField(name = "start_time")
    String startTime;
    List<Trace> trace;
    String caller;
    @JSONField(name = "@timestamp")
    String timestamp;
    String answer;
    @JSONField(name = "flow_id")
    String flowId;
    @JSONField(name = "logstash_hostname")
    String logstashHostname;
    @JSONField(name = "app_id")
    String appId;
    @JSONField(name = "bot_id")
    String botId;
    Status status;
}
