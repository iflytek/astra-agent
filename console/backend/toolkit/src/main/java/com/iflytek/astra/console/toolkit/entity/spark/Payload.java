package com.iflytek.astra.console.toolkit.entity.spark;

import com.iflytek.astra.console.toolkit.entity.spark.request.FcFunction;
import com.iflytek.astra.console.toolkit.entity.spark.request.Message;
import com.iflytek.astra.console.toolkit.entity.spark.response.Choices;
import com.iflytek.astra.console.toolkit.entity.spark.response.Usage;
import lombok.Data;

@Data
public class Payload {
    // request
    Message message;

    // response
    Choices choices;
    Usage usage;

    FcFunction functions;
}
