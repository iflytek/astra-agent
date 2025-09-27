package com.iflytek.astron.console.toolkit.entity.table.eval;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.iflytek.astron.console.toolkit.common.anno.ExcelHeader;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@TableName("effect_eval_task_report")
public class EvalTaskReport {
    @TableId(type = IdType.AUTO)
    Long id;
    Long evalTaskId;
    String taskId;
    Integer seq;
    // @ExcelHeader(value = "SID", order = 1)
    String sid;
    @ExcelHeader(value = "User Input", order = 1)
    String question;
    @ExcelHeader(value = "Actual Output", order = 2)
    String answer;
    // @ExcelHeader(value = "Expected Answer", order = 4)
    String expectedAnswer;
    // @ExcelHeader(value = "Performance Duration", order = 5)
    Double totalTimeCost;
    // @ExcelHeader(value = "First Frame Duration", order = 6)
    Double firstFrameCost;
    // @ExcelHeader(value = "F1 Score", order = 7)
    Double f1Score;
    // @ExcelHeader(value = "Recall Rate", order = 8)
    Double recall;
    @TableField("`precision`")
    // @ExcelHeader(value = "Accuracy Rate", order = 9)
    Double precision;
    @TableField("`status`")
    Integer status;
    Object markData;
    Date createTime;
    Date updateTime;
    String trace;
    String tag1;
    Integer errorCode;
    /**
     * Judgment status logic: errorCode() == 0 ? "success" : "failure"
     */
    // @ExcelHeader(value = "Status", order = 10)
    Integer chatErrCode;
    @ExcelHeader(value = "Detail Score", order = 3)
    Integer score;
    @ExcelHeader(value = "Score Reason", order = 4)
    String scoreDesc;
    Integer token;
    String errorMsg;
    String chatErrMsg;
    String dimension;
    Integer isDelete;
    /**
     * 2 - Manual, 3 - Intelligent
     */
    Integer taskMode;
    /**
     * Whether scored
     */
    Boolean isScored;

    @TableField(exist = false)
    private Integer humanScore;
    @TableField(exist = false)
    private String humanScoreDesc;

    @TableField(exist = false)
    private Integer aiScore;
    @TableField(exist = false)
    private String aiScoreDesc;

    // Parameter answer
    String parameterAnswer;
    @TableField(exist = false)
    private JSONObject jsonParameterAnswer;

    // Multi-parameter input
    String parameterQuestion;
    @TableField(exist = false)
    private List<JSONObject> listJsonParameterQuestion;
}
