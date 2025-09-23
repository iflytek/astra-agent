package com.iflytek.astron.console.toolkit.common.constant;

public class CommonConst {
    public static final String[] FIXED_APPID_ENV = new String[] {"dev", "test", "custom"};
    public static final String[] FIXED_APPID_ENV_PRO = new String[] {"dev", "test", "custom", "pre", "prod"};
    public static final String ENV_DEV = "dev";

    public static final String LOCAL_TMP_WORK_DIR = "/tmp/agent-builder/";
    public static final String SERVER_PREFIX = "sws@";
    /**
     * Application content placeholder information
     */
    public static final String AUTH_CONTENT_PLACEHOLDER = "{\"conc\":2,\"domain\":\"generalv3.5\",\"expireTs\":\"2025-05-31\",\"qps\":2,\"tokensPreDay\":1000,\"tokensTotal\":1000,\"llmServiceId\":\"bm3.5\"}";

    /**
     * Spark model
     */
    public static final int LLM_TYPE_SPARK = 1;

    /**
     * Open source model
     */
    public static final int LLM_TYPE_OPEN = 2;

    /**
     * Fine-tuned Spark model
     */
    public static final int FT_MODEL_TYPE_SPARK = 1;

    /**
     * Fine-tuned open source model
     */
    public static final int FT_MODEL_TYPE_OPEN = 0;

    /**
     * Model marketplace source
     */
    public static final int LLM_SOURCE_SQUARE = 1;

    /**
     * Fine-tuning platform source
     */
    public static final int LLM_SOURCE_FINE_TUNE = 2;

    /**
     * Unauthorized
     */
    public static final int AUTH_STATUS_NOT_APPLY = -1;

    /**
     * Authorizing
     */
    public static final int AUTH_STATUS_APPLYING = 0;

    /**
     * Authorized
     */
    public static final int AUTH_STATUS_AUTHED = 1;

    public static final String A_VERY_LONG_LATER_DATE = "2099-12-31";
    public static final String A_VERY_LONG_TIME_LATER_TS_SECOND = "1893427200"; // aka 2030-01-01 00:00:00

    public static final String SID_PREFIX = "agent_";
    public static final String ALL_FILE_LIMIT_COUNT = "all_file_limit_count";

    public static class AutoAuthStatus {
        public static final int WAITING = 1;

        public static final int THIS_APP_AUTHED = 2;

        public static final int NOT_THIS_APP_AUTHED = 3;

        public static final int EXHAUST_OR_EXPIRED = 4;
    }

    public static class AutoAuthContent {
        public static final String LITE = "";
    }

    public static final int MEDIUM_TEXT_BYTES_LIMIT = 16777215;

    public static final class ApplicationType {
        public static final int AGENT = 1;
        public static final int WORKFLOW = 2;
        public static final int PROMPT = 3;
        public static final int NONE = -1;

    }

    public static final class Platform {
        public static final String XFYUN = "xfyun";
        public static final String IFLYAICLOUD = "iflyaicloud";
        public static final String AIUI = "aiui";
        public static final String COMMON = "common";
    }

    public static final class PlatformCode {
        public static final int XFYUN = 2;
        public static final int AIUI = 3;
        public static final int COMMON = 1;
    }

    public static final class SystemCaller {
        public static final String SPARK_EVALUATE = "sparkevaluate";
        public static final String WEB_SERVICE = "sparkWebservice";
        public static final String WEB_SERVICE_COPY = "webserviceCopy";
    }

    public static final class DBFieldType {
        public static final String STRING = "string";
        public static final String TIME = "time";
        public static final String INTEGER = "integer";
        public static final String BOOLEAN = "boolean";
        public static final String NUMBER = "number";
    }
}
