package com.iflytek.astron.console.toolkit.entity.enumVo;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ScoreEnum {
    GOOD(4, "Good"),
    BETTER(3, "Better"),
    NORMAL(2, "Average"),
    LESS(1, "Poor"),
    BAD(0, "Bad");

    final int scoreVal;
    final String scoreDesc;

    public static Integer getValByDesc(String desc) {
        for (ScoreEnum value : ScoreEnum.values()) {
            if (value.getScoreDesc().equals(desc)) {
                return value.getScoreVal();
            }
        }

        return null;
    }

    public static String getDescByVal(int val) {
        for (ScoreEnum value : ScoreEnum.values()) {
            if (value.getScoreVal() == val) {
                return value.getScoreDesc();
            }
        }

        return null;
    }
}
