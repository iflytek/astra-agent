package com.iflytek.astra.console.toolkit.common.constant;

import com.alibaba.fastjson2.JSONObject;

public class EffectEvalConst {
    public static final String EVAL_TMP_WORK_DIR = CommonConst.LOCAL_TMP_WORK_DIR + "eval/";

    public static final String SET_S3_PREFIX = "sparkBot/evalSet/";
    public static final JSONObject FINE_TUNE_OPEN_DATASET_TEMPLATE = new JSONObject()
            .fluentPut("input", "input")
            .fluentPut("output", "output")
            .fluentPut("instruction", "");
    public static final int FINE_TUNE_OPEN_MODEL_TRAIN_DATA_MIN_SIZE = 50;
    public static final CharSequence SUPPORT_FILE_SUFFIX = "csv";
    public static final long SUPPORT_FILE_MAX_SIZE = 20971520L;

    public static final JSONObject FC_TEMPLATE = new JSONObject()
            .fluentPut("name", "name")
            .fluentPut("arguments",
                    new JSONObject().fluentPut("next_inputs", "next_inputs"));

    public static final class GetDataMode {
        public static final int ONLINE = 1; // 1:线上
        public static final int OFFLINE = 2; // 2:线下
    }

    public static final class DataSource {
        public static final int OFFLINE = 1;
        public static final int ONLINE = 2;
    }

    public static final class DataReportSource {
        // 已终止
        public static final int TERMINATED_ALREADY = -1;
        // 待评分
        public static final int ToBeRated = 0;
        // 评分失败
        public static final int RateFailed = -2;
        // 参数缺失
        public static final int MissParameter = -3;
        // 智能测评打分理由，请前往测评集管理编辑补充后再尝试
        public static final int MissParameterScoreReason = -4;

    }

    public static final class EvalTaskStatus {
        /**
         * 评测中，数据跑批中
         */
        public static final int DATA_RUNNING = 0;
        /**
         * 评测完成
         */
        public static final int EVALUATED = 1;
        /**
         * 评测失败
         */
        public static final int FAIL = 2;
        /**
         * 已标注
         */
        public static final int MARKED = 3;

        /**
         * 评测中，数据跑批完成但未打分
         */
        public static final int DATA_NOT_SCORED = 4;

        /**
         * 暂定
         */
        public static final int PAUSE = 5;

        /**
         * 终止中
         */
        public static final int TERMINATED = 6;

        /**
         * 服务shutdown导致的停止
         */
        public static final int SERVER_SHUTDOWN = -1;

        /**
         * 创建中
         */
        public static final int STORE_TEMPORARY = 8;

        /**
         * 已终止
         */
        public static final int TERMINATED_ALREADY = 9;

        /**
         * 评分中
         */
        public static final int DATA_SCORED = 10;
    }

    public static final class SparkEvaluateTaskStatus {
        public static final int RUNNING = 0;
        public static final int SUCCEED = 1;
        public static final int FAIL = 2;
    }

    public static final class OptimizeTaskStatus {
        public static final int INIT = 0;

        public static final int RUNNING = 1;
        public static final int SUCCEED = 2;
        public static final int FAIL = 3;
        public static final int PENDING = 4;
        public static final int STOPPED = 5;
    }

    public static final class Scheme {
        public static final int ELEMENT_PICK_UP = 1;
        public static final int STRING_MATCHING = 2;
    }

    public static final class ReportDataStatus {
        public static final int UN_MARKED = 0;
        public static final int MARKED = 1;
    }

    public static final class SampleMode {
        /**
         * 顺序
         */
        public static final int SEQUENTIAL = 1;
        /**
         * 随机
         */
        public static final int RANDOM = 2;
        /**
         * 点赞点踩
         */
        public static final int FEEDBACK = 3;

    }

    public static final class TaskMode {
        /**
         * 批量数据测试
         */
        public static final int ONLY_DATA_BATCH = 1;
        /**
         * 人工评测
         */
        public static final int MANUAL_EVALUATE = 2;
        /**
         * 自动评测
         */
        public static final int AUTO_EVALUATE = 3;

    }

    /**
     * 0=未部署，1=部署中，2=部署失败，3=部署成功 枚举同微调侧
     */
    public static final class ModelServerStatus {
        public static final int UNDEPLOY = 0;
        public static final int DEPLOYING = 1;
        public static final int DEPLOY_FAILED = 2;
        public static final int DEPLOY_SUCCESS = 3;

    }

}
