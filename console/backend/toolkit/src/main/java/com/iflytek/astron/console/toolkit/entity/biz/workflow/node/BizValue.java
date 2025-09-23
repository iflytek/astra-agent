package com.iflytek.astron.console.toolkit.entity.biz.workflow.node;


import lombok.Data;

@Data
public class BizValue {
    String type;
    Object content;
    String contentErrMsg;
}
