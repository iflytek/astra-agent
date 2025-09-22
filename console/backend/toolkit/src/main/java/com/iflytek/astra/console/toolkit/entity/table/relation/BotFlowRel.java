package com.iflytek.astra.console.toolkit.entity.table.relation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.*;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BotFlowRel {
    @TableId(type = IdType.AUTO)
    Long id;
    Long botId;
    String flowId;
    Date createTime;
}
