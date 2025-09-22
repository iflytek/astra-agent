package com.iflytek.astra.console.toolkit.entity.core.workflow.ws;


import com.iflytek.astra.console.toolkit.entity.spark.Parameter;
import com.iflytek.astra.console.toolkit.entity.spark.chat.Payload;
import lombok.Data;

@Data
public class SparkFlowResponse {
    SparkFlowResponseHeader header;
    Payload payload;
    Parameter parameter;
}
