package com.iflytek.astron.console.toolkit.entity.spark.response;

import lombok.Data;

@Data
public class UsageText {
    Integer question_tokens;
    Integer prompt_tokens;
    Integer completion_tokens;
    Integer total_tokens;
}
