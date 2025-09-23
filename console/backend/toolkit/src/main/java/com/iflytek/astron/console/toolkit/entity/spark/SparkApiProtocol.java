package com.iflytek.astron.console.toolkit.entity.spark;


import lombok.Data;

@Data
public class SparkApiProtocol {
    Header header;
    Parameter parameter;
    Payload payload;
}
