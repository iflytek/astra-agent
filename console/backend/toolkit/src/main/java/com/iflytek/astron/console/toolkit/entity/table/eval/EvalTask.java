package com.iflytek.astron.console.toolkit.entity.table.eval;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@TableName("effect_eval_task")
public class EvalTask {
    // December transformation reserved fields
    @TableId(type = IdType.AUTO)
    Long id;
    String uid;
    String name;
    String applicationId;
    Integer applicationType;
    Integer evalMode;
    Date sampleStartTime;
    Date sampleEndTime;
    Integer sampleAmount;
    Integer sampleMode;
    @TableField("`status`")
    Integer status;
    Date createTime;
    Date updateTime;
    Boolean deleted;
    Integer evalScheme;
    String taskErrInfo;
    Double f1Score;
    @TableField("`precision`")
    Double precision;
    Double recall;
    /**
     * Evaluation task ID
     */
    String taskId;

    // December additions
    /**
     * Task mode 1=batch data test, 2=manual, 3=auto evaluation, combination
     */
    String taskMode;
    Integer applicationStatus;
    Boolean scored;
    String dataListConfig;

    // December transformation changed fields
    String evalSetId;
    String evalSetVerId;

    Integer dataSuccCount;
    Integer dataFailCount;
    Integer dataCount;

    String dimensions;
    String applicationVersion;
    String applicationVersionId;
    String applicationPrompt;
    String storeTemporaryData;
    String dimensionPrompts;
    Integer seamlessStatus;

    Long spaceId;

    // July 2025 transformation changed fields - dataset version header ID
    String evalSetVerHeaderId;
    // Prompt evaluation multiple parameters
    @TableField(exist = false)
    List<List<String>> promptVo;

    // Model type 1: deepseekV3 2: deepseekR1 3: spark x1
    Integer judgeModel;
}
