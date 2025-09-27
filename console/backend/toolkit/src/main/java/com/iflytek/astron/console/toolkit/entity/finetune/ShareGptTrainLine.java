package com.iflytek.astron.console.toolkit.entity.finetune;

import lombok.Data;

import java.util.List;

@Data
public class ShareGptTrainLine {
    List<Conversation> conversations;
    String tools;
    String system;
}
