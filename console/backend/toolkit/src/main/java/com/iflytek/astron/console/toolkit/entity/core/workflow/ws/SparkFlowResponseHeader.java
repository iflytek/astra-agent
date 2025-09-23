package com.iflytek.astron.console.toolkit.entity.core.workflow.ws;

import com.alibaba.fastjson2.annotation.JSONField;
import com.iflytek.astron.console.toolkit.entity.spark.chat.Header;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class SparkFlowResponseHeader extends Header {
    @JSONField(name = "chat_id")
    String chatId;

    @JSONField(name = "flow_status")
    Integer flowStatus;

    Step step;

    @JSONField(name = "flow_progress")
    Integer flowProgress;

    @JSONField(name = "flow_data_type")
    String flowDataType;

    @JSONField(name = "flow_time_cost")
    String flowTimeCost;
}
