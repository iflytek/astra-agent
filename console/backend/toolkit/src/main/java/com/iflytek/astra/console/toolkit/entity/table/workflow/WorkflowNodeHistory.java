package com.iflytek.astra.console.toolkit.entity.table.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.*;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeHistory {
    @TableId(type = IdType.AUTO)
    Long id;
    String flowId;
    String nodeId;
    String chatId;
    String rawQuestion;
    String rawAnswer;
    Date createTime;
}
