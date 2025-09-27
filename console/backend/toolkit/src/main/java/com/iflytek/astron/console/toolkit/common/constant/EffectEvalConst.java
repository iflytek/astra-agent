package com.iflytek.astron.console.toolkit.common.constant;

import com.alibaba.fastjson2.JSONObject;

/**
 * Constants for evaluation tasks and related configurations.
 * <p>
 * Includes directory paths, dataset templates, file limits, and enumerations for evaluation modes,
 * statuses, and task types.
 * </p>
 */
public class EffectEvalConst {

    /**
     * Local temporary work directory for evaluation.
     */
    public static final String EVAL_TMP_WORK_DIR = CommonConst.LOCAL_TMP_WORK_DIR + "eval/";

    /**
     * Prefix path for uploading evaluation sets to S3.
     */
    public static final String SET_S3_PREFIX = "sparkBot/evalSet/";

    /**
     * Template JSON for fine-tuning open dataset.
     */
    public static final JSONObject FINE_TUNE_OPEN_DATASET_TEMPLATE = new JSONObject()
            .fluentPut("input", "input")
            .fluentPut("output", "output")
            .fluentPut("instruction", "");

    /**
     * Minimum data size required for fine-tuning open model training.
     */
    public static final int FINE_TUNE_OPEN_MODEL_TRAIN_DATA_MIN_SIZE = 50;

    /**
     * Supported file suffix for dataset.
     */
    public static final CharSequence SUPPORT_FILE_SUFFIX = "csv";

    /**
     * Maximum supported file size (20MB).
     */
    public static final long SUPPORT_FILE_MAX_SIZE = 20971520L;

    /**
     * Template JSON for function call.
     */
    public static final JSONObject FC_TEMPLATE = new JSONObject()
            .fluentPut("name", "name")
            .fluentPut("arguments",
                    new JSONObject().fluentPut("next_inputs", "next_inputs"));

    /**
     * Modes for obtaining data.
     */
    public static final class GetDataMode {
        public static final int ONLINE = 1; // Online mode
        public static final int OFFLINE = 2; // Offline mode
    }

    /**
     * Data source types.
     */
    public static final class DataSource {
        public static final int OFFLINE = 1;
        public static final int ONLINE = 2;
    }

    /**
     * Data report source statuses.
     */
    public static final class DataReportSource {
        /** Terminated already */
        public static final int TERMINATED_ALREADY = -1;
        /** To be rated */
        public static final int ToBeRated = 0;
        /** Rating failed */
        public static final int RateFailed = -2;
        /** Missing parameter */
        public static final int MissParameter = -3;
        /** Missing parameter score reason, please edit and supplement in dataset management */
        public static final int MissParameterScoreReason = -4;
    }

    /**
     * Evaluation task statuses.
     */
    public static final class EvalTaskStatus {
        /** Evaluating, data batch running */
        public static final int DATA_RUNNING = 0;
        /** Evaluation completed */
        public static final int EVALUATED = 1;
        /** Evaluation failed */
        public static final int FAIL = 2;
        /** Marked */
        public static final int MARKED = 3;
        /** Evaluating, data batch finished but not scored */
        public static final int DATA_NOT_SCORED = 4;
        /** Paused */
        public static final int PAUSE = 5;
        /** Terminating */
        public static final int TERMINATED = 6;
        /** Stopped due to service shutdown */
        public static final int SERVER_SHUTDOWN = -1;
        /** Creating */
        public static final int STORE_TEMPORARY = 8;
        /** Terminated already */
        public static final int TERMINATED_ALREADY = 9;
        /** Scoring in progress */
        public static final int DATA_SCORED = 10;
    }

    /**
     * Spark evaluation task statuses.
     */
    public static final class SparkEvaluateTaskStatus {
        public static final int RUNNING = 0;
        public static final int SUCCEED = 1;
        public static final int FAIL = 2;
    }

    /**
     * Optimization task statuses.
     */
    public static final class OptimizeTaskStatus {
        public static final int INIT = 0;
        public static final int RUNNING = 1;
        public static final int SUCCEED = 2;
        public static final int FAIL = 3;
        public static final int PENDING = 4;
        public static final int STOPPED = 5;
    }

    /**
     * Evaluation schemes.
     */
    public static final class Scheme {
        public static final int ELEMENT_PICK_UP = 1;
        public static final int STRING_MATCHING = 2;
    }

    /**
     * Report data statuses.
     */
    public static final class ReportDataStatus {
        public static final int UN_MARKED = 0;
        public static final int MARKED = 1;
    }

    /**
     * Sampling modes.
     */
    public static final class SampleMode {
        /** Sequential */
        public static final int SEQUENTIAL = 1;
        /** Random */
        public static final int RANDOM = 2;
        /** Feedback (like/dislike) */
        public static final int FEEDBACK = 3;
    }

    /**
     * Task modes.
     */
    public static final class TaskMode {
        /** Batch data testing */
        public static final int ONLY_DATA_BATCH = 1;
        /** Manual evaluation */
        public static final int MANUAL_EVALUATE = 2;
        /** Automatic evaluation */
        public static final int AUTO_EVALUATE = 3;
    }

    /**
     * Model server deployment statuses (shared with fine-tuning side).
     * <p>
     * 0 = not deployed, 1 = deploying, 2 = deploy failed, 3 = deploy succeeded
     * </p>
     */
    public static final class ModelServerStatus {
        public static final int UNDEPLOY = 0;
        public static final int DEPLOYING = 1;
        public static final int DEPLOY_FAILED = 2;
        public static final int DEPLOY_SUCCESS = 3;
    }
}
