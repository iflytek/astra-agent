package com.iflytek.astra.console.toolkit.common.constant;

public class WorkflowConst {
    public static final int LLM_RESP_FORMAT_TEXT = 0;
    public static final int LLM_RESP_FORMAT_JSON = 2;

    public static class Status {
        public static final int UNPUBLISHED = 0;
        public static final int PUBLISHED = 1;
    }

    public static class NodeType {
        public static final String START = "node-start";
        public static final String END = "node-end";
        public static final String SPARK_LLM = "spark-llm";
        public static final String DECISION_MAKING = "decision-making";
        public static final String EXTRACTOR_PARAMETER = "extractor-parameter";
        public static final String MESSAGE = "message";
        public static final String FLOW = "flow";
        public static final String QUESTION_ANSWER = "question-answer";
        public static final String PLUGIN = "plugin";
        public static final String KNOWLEDGE = "knowledge-base";
        public static final String KNOWLEDGE_PRO = "knowledge-pro-base";
        public static final String AGENT = "agent";
        public static final String FLOW_END = "flow_obj";
        public static final String DATABASE = "database";

    }

    public static class ReleaseChannel {
        public static final String API = "api";
        public static final String IXF_PERSONAL = "ixf-personal";
        public static final String IXF_TEAM = "ixf-team";
        public static final String AIUI = "aiui";
        public static final String SPARK_DESK = "sparkdesk";
        public static final String SQUARE = "square";
        public static final String MCP = "mcp";
    }

    public static class FlowAnswerMode {
        public static final int PARAMETERS = 0;
        public static final int SETUP_FORMAT = 1;

    }

    public static class ConfigCategory {
        public static final String WORKFLOW_SQUARE_TYPE = "WORKFLOW_SQUARE_TYPE";

    }


}
