package com.iflytek.astron.console.toolkit.entity.table.workflow;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.Date;

@Data
public class WorkflowVersion {
    @TableId(type = IdType.AUTO)
    Long id;
    String botId;
    String name;
    String versionNum;
    // Workflow protocol data
    String data;
    String flowId;
    Long deleted;
    // Publish time
    Date createdTime;
    Date updatedTime;
    Long isVersion;
    // Core system protocol data
    String sysData;
    String description;
    // Publish channel
    Long publishChannel;
    // Publish data
    String publishResult;
}
