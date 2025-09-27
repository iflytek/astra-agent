"""
Error code definitions for the workflow system.

This module defines all error codes used throughout the workflow system,
organized by functional categories. Each error code includes both a numeric
code and a descriptive message for consistent error handling and user feedback.

The error codes are structured hierarchically with specific ranges for
different functional areas, making it easy to identify and handle errors
appropriately throughout the system. This systematic organization enables:

- Easy error identification and categorization
- Consistent error handling across all system components
- Simplified debugging and troubleshooting
- Clear error reporting to users and administrators
- Integration with monitoring and alerting systems
"""

from enum import Enum


class CodeEnum(Enum):
    """
    Enumeration of all error codes used in the workflow system.
    """

    Success = (0, "Success")

    PARAM_ERROR = (460, "Parameter validation error")

    # Application errors
    APP_NOT_FOUND_ERROR = (20000, "Application not found")
    APP_GET_WITH_REMOTE_FAILED_ERROR = (
        20001,
        "Failed to get application from management platform",
    )
    APP_TENANT_NOT_FOUND_ERROR = (20002, "Application tenant not found")
    APP_TENANT_PLATFORM_UNAUTHORIZED_ERROR = (
        20003,
        "Application tenant has no platform permission",
    )
    APP_FLOW_NOT_AUTH_BOND_ERROR = (20004, "Application not bound to workflow")
    APP_FLOW_NO_LICENSE_ERROR = (20005, "Appid is prohibited from using this workflow")
    APP_PLAT_NOT_RELEASE_OP_ERROR = (20006, "Platform has no corresponding operation")
    APP_FLOW_AUTH_BOND_ERROR = (20007, "Application binding failed")

    # Protocol errors
    PROTOCOL_VALIDATION_ERROR = (20100, "Protocol validation failed")
    PROTOCOL_BUILD_ERROR = (20101, "Protocol build failed")
    PROTOCOL_CREATE_ERROR = (20102, "Protocol creation error")
    PROTOCOL_DELETE_ERROR = (20103, "Protocol deletion error")
    PROTOCOL_UPDATE_ERROR = (20104, "Protocol update failed")

    # Flow errors
    FLOW_NOT_FOUND_ERROR = (20201, "Flow ID not found")
    FLOW_ID_TYPE_ERROR = (20202, "Invalid Flow ID")
    FLOW_NOT_PUBLISH_ERROR = (20204, "Workflow not published")
    FLOW_NO_APP_ID_ERROR = (20205, "Workflow has no appid")
    FLOW_PUBLISH_ERROR = (20206, "Workflow publish failed")
    OPEN_API_FLOW_STATUS_ERROR = (20207, "Workflow is in draft status")  # DEPRECATED
    FLOW_VERSION_ERROR = (20208, "Flow version not found")
    FLOW_GET_ERROR = (20209, "Workflow retrieval failed")

    # Spark model errors
    SPARK_FUNCTION_NOT_CHOICE_ERROR = (20301, "Model did not select valid function")
    SPARK_QUICK_REPAIR_ERROR = (20302, "Model hit specific issue")
    SPARK_REQUEST_ERROR = (20303, "Model request failed")
    # Spark service error code mapping 20350-20376
    SPARK_WS_ERROR = (20350, "Model request upgrade to WebSocket error")
    SPARK_WS_READ_ERROR = (20351, "Model request WebSocket read user message error")
    SPARK_WS_SEND_ERROR = (20352, "Model request WebSocket send message to user error")
    SPARK_MESSAGE_FORMAT_ERROR = (20353, "Model request user message format error")
    SPARK_SCHEMA_ERROR = (20354, "Model request user data schema error")
    SPARK_PARAM_ERROR = (20355, "Model request user parameter value error")
    SPARK_CONCURRENCY_ERROR = (
        20356,
        "Model service user concurrency error: current user is connected, "
        "same user cannot connect from multiple locations",
    )
    SPARK_TRAFFIC_LIMIT_ERROR = (
        20357,
        "Model service user traffic limit: service is processing user's current question, "
        "wait for completion before sending new request",
    )
    SPARK_CAPACITY_ERROR = (20358, "Model service capacity insufficient, contact staff")
    SPARK_ENGINE_CONNECTION_ERROR = (
        20359,
        "Model and engine connection establishment failed",
    )
    SPARK_ENGINE_RECEIVE_ERROR = (20360, "Model receiving engine data error")
    SPARK_ENGINE_SEND_ERROR = (20361, "Model service sending data to engine error")
    SPARK_ENGINE_INTERNAL_ERROR = (20362, "Model engine internal error")
    SPARK_CONTENT_AUDIT_ERROR = (
        20363,
        "Model input content audit failed, suspected violation, please adjust input content",
    )
    SPARK_OUTPUT_AUDIT_ERROR = (
        20364,
        "Model output content involves sensitive information, audit failed, results cannot be displayed to user",
    )
    SPARK_APP_ID_BLACKLIST_ERROR = (20365, "Appid is in model service blacklist")
    SPARK_APP_ID_AUTH_ERROR = (
        20366,
        "Model service appid authorization error: feature not enabled, "
        "version not enabled, insufficient tokens, concurrency exceeds authorization",
    )
    SPARK_CLEAR_HISTORY_ERROR = (20367, "Model clear history failed")
    SPARK_VIOLATION_ERROR = (
        20368,
        "Model service indicates session content has violation tendency; "
        "suggest showing violation warning to user",
    )
    SPARK_BUSY_ERROR = (20369, "Model service busy, please try again later")
    SPARK_ENGINE_PARAMS_ERROR = (
        20370,
        "Model service request engine parameter error, engine schema check failed",
    )
    SPARK_ENGINE_NETWORK_ERROR = (20371, "Model service engine network error")
    SPARK_TOKEN_LIMIT_ERROR = (
        20372,
        "Model request token count exceeds limit, conversation history + question text too long, "
        "need to simplify input",
    )
    SPARK_AUTH_ERROR = (
        20373,
        "Model authorization error: appId has no feature authorization or business volume exceeds limit",
    )
    SPARK_DAILY_LIMIT_ERROR = (
        20374,
        "Model authorization error: daily rate limit exceeded",
    )
    SPARK_SECOND_LIMIT_ERROR = (
        20375,
        "Model authorization error: second-level rate limit exceeded",
    )
    SPARK_CONCURRENCY_LIMIT_ERROR = (
        20376,
        "Model authorization error: concurrency limit exceeded",
    )

    OPEN_AI_REQUEST_ERROR = (20380, "External large model request failed")

    # 20400

    # Knowledge base errors
    KNOWLEDGE_REQUEST_ERROR = (20500, "Knowledge base request error")
    KNOWLEDGE_NODE_EXECUTION_ERROR = (20501, "Knowledge base node execution error")
    KNOWLEDGE_PARAM_ERROR = (20502, "Knowledge base parameter error")

    # Variable pool errors
    VARIABLE_POOL_GET_PARAMETER_ERROR = (
        20600,
        "Variable system parameter retrieval failed",
    )
    VARIABLE_POOL_SET_PARAMETER_ERROR = (
        20601,
        "Variable system parameter setting failed",
    )
    VARIABLE_PARSE_ERROR = (20602, "Variable name parsing failed")

    # 20700

    # OpenAPI errors
    OPEN_API_STREAM_QUEUE_TIMEOUT_ERROR = (20804, "OpenAPI output timeout")
    OPEN_API_ERROR = (20805, "OpenAPI output error")

    # Authentication and rate limiting errors
    MASDK_LICC_LIMIT_ERROR = (
        20900,
        "Authentication failed: authorization limit, service not authorized or authorization expired",
    )
    MASDK_OVER_LIMIT_ERROR = (
        20901,
        "Authentication failed: service limit exceeded, "
        "business session total limit or daily rate limit exceeded",
    )
    MASDK_OVER_QPS_LIMIT_ERROR = (
        20902,
        "Authentication failed: service limit exceeded, QPS second-level rate limit exceeded",
    )
    MASDK_OVER_CONC_LIMIT_ERROR = (
        20903,
        "Concurrency authentication failed: service limit exceeded, concurrency limit exceeded",
    )
    MASDK_CONNECT_ERROR = (
        20904,
        "Authentication SDK error, non-success status no log returned",
    )
    MASDK_UNKNOWN_ERROR = (20905, "Authentication failed, unknown error")

    # PostgreSQL node errors
    PG_SQL_REQUEST_ERROR = (21000, "PostgreSQL node request error")
    PG_SQL_NODE_EXECUTION_ERROR = (21001, "PostgreSQL node execution error")
    PG_SQL_PARAM_ERROR = (21002, "PostgreSQL node request parameter error")

    # Audit errors
    AUDIT_ERROR = (21100, "Audit error")
    AUDIT_SERVER_ERROR = (21101, "Audit service error")
    AUDIT_INPUT_ERROR = (
        21102,
        "Workflow input content audit failed, suspected violation, please adjust input content",
    )
    AUDIT_OUTPUT_ERROR = (
        21103,
        "Workflow output content involves sensitive information, audit failed, "
        "results cannot be displayed to user",
    )
    AUDIT_QA_ERROR = (21104, "Question-answer node does not support audit yet")

    # Image generation errors   # DEPRECATED
    IMAGE_GENERATE_ERROR = (21200, "Image generation failed")  # DEPRECATED
    IMAGE_STORAGE_ERROR = (21201, "Image storage failed")  # DEPRECATED
    IMAGE_GENERATE_MSG_FORMAT_ERROR = (21203, "User message format error")  # DEPRECATED
    IMAGE_GENERATE_SCHEMA_ERROR = (21204, "User data schema error")  # DEPRECATED
    IMAGE_GENERATE_PARAMS_ERROR = (21205, "User parameter value error")  # DEPRECATED
    IMAGE_GENERATE_SRV_NOT_ENOUGH_ERROR = (
        21206,
        "Image generation service capacity insufficient",
    )  # DEPRECATED
    IMAGE_GENERATE_INPUT_AUDIT_ERROR = (
        21207,
        "Image generation service input audit failed",
    )  # DEPRECATED
    IMAGE_GENERATE_IMAGE_SENSITIVENESS_ERROR = (
        21208,
        "Model generated image involves sensitive information, audit failed",
    )  # DEPRECATED
    IMAGE_GENERATE_IMAGE_TIMEOUT_ERROR = (
        21209,
        "Image generation timeout",
    )  # DEPRECATED

    # 21300

    # 21400

    # 21500

    # Code interpreter errors
    CODE_EXECUTION_ERROR = (21600, "Code execution failed")
    CODE_BUILD_ERROR = (21601, "Code interpreter node build failed")
    CODE_NODE_RESULT_TYPE_ERROR = (
        21602,
        "Code node return result type does not meet requirements",
    )
    CODE_EXECUTION_TIMEOUT_ERROR = (21603, "Code execution timeout")

    # Node debug related errors
    NODE_DEBUG_ERROR = (21700, "Node debug failed")

    # SparkLink related errors
    SPARK_LINK_ACTION_ERROR = (21800, "Tool request failed")
    SPARK_LINK_APP_INIT_ERROR = (21801, "Tool initialization failed")
    SPARK_LINK_JSON_PROTOCOL_PARSER_ERROR = (21802, "Tool JSON protocol parsing failed")
    SPARK_LINK_JSON_SCHEMA_VALIDATE_ERROR = (21803, "Tool protocol validation failed")
    SPARK_LINK_OPENAPI_SCHEMA_VALIDATE_ERROR = (
        21804,
        "Tool OpenAPI protocol parsing failed",
    )
    SPARK_LINK_OPENAPI_SCHEMA_BODY_TYPE_NOT_SUPPORT_ERROR = (
        21805,
        "Tool body type not supported",
    )
    SPARK_LINK_OPENAPI_SCHEMA_SERVER_NOT_EXIST_ERROR = (
        21806,
        "Tool server does not exist",
    )
    SPARK_LINK_OFFICIAL_THIRD_API_REQUEST_FAILED_ERROR = (
        21807,
        "Official tool request failed",
    )
    SPARK_LINK_TOOL_NOT_EXIST_ERROR = (21808, "Tool does not exist")
    SPARK_LINK_OPERATION_ID_NOT_EXIST_ERROR = (21809, "Tool operation does not exist")
    SPARK_LINK_CONNECTION_ERROR = (21810, "Tool request failed, connection error")
    SPARK_LINK_EXECUTE_ERROR = (21811, "Third-party tool execution failed")
    SPARK_LINK_THIRD_API_REQUEST_FAILED_ERROR = (
        21812,
        "Third-party tool request failed",
    )

    # Parameter extractor errors
    EXTRACT_EXECUTION_ERROR = (21900, "Parameter extraction failed")

    # 22000

    # Protocol validation errors
    ENG_PROTOCOL_VALIDATE_ERROR = (22100, "Workflow engine protocol validation failed")
    ENG_NODE_PROTOCOL_VALIDATE_ERROR = (
        22101,
        "Workflow engine node protocol validation failed",
    )

    # 22200

    # Engine errors
    ENG_BUILD_ERROR = (22300, "Workflow engine build failed")
    ENG_RUN_ERROR = (22301, "Workflow engine run failed")
    NODE_RUN_ERROR = (22302, "Node execution failed")

    # 22400

    # Start node errors
    START_NODE_SCHEMA_ERROR = (22500, "Start node protocol error")

    # End node errors
    END_NODE_SCHEMA_ERROR = (22600, "End node protocol error")
    END_NODE_EXECUTION_ERROR = (22601, "End node execution failed")

    # Message node errors
    MESSAGE_NODE_EXECUTION_ERROR = (22701, "Message node execution failed")

    # Workflow node errors
    WORKFLOW_EXECUTION_ERROR = (22801, "Workflow node execution failed")
    WORKFLOW_EXEC_RESP_FORMAT_ERROR = (
        22802,
        "Workflow node execution response format error",
    )

    # Variable node errors
    VARIABLE_NODE_EXECUTION_ERROR = (22900, "Variable node execution failed")

    # 23000

    # Branch node errors
    IF_ELSE_NODE_EXECUTION_ERROR = (23100, "Branch node execution failed")

    # Iteration node errors
    ITERATION_EXECUTION_ERROR = (23200, "Iteration node execution failed")

    # LLM node errors
    LLM_NODE_EXECUTION_ERROR = (23300, "LLM node execution failed")

    # Plugin node errors
    PLUGIN_NODE_EXECUTION_ERROR = (23400, "Plugin node execution failed")

    # Text joiner node errors
    TEXT_JOINER_NODE_EXECUTION_ERROR = (23500, "Text joiner node execution failed")

    # File type errors
    FILE_INVALID_ERROR = (23601, "Invalid file")
    FILE_VARIABLE_PROTOCOL_ERROR = (23602, "File variable protocol error")
    FILE_INVALID_TYPE_ERROR = (23603, "Invalid file link")
    FILE_STORAGE_ERROR = (23604, "File storage failed")

    # Agent node errors
    AGENT_NODE_EXECUTION_ERROR = (23700, "Agent node execution failed")

    # Question-answer node errors
    QUESTION_ANSWER_NODE_EXECUTION_ERROR = (
        23800,
        "Question-answer node execution failed",
    )
    QUESTION_ANSWER_NODE_PROTOCOL_ERROR = (23801, "Question-answer node protocol error")
    QUESTION_ANSWER_RESUME_DATA_ERROR = (
        23802,
        "Question-answer node data retrieval timeout",
    )
    QUESTION_ANSWER_HANDLER_RESPONSE_ERROR = (
        23803,
        "Question-answer node user response handling error",
    )

    # Event management errors
    EVENT_REGISTRY_NOT_FOUND_ERROR = (
        23900,
        "Conversation has timed out or does not exist",
    )

    @property
    def code(self) -> int:
        """
        Get the numeric error code.

        Returns the integer error code that can be used for programmatic error
        identification, logging, and error handling logic. The error codes are
        organized in specific ranges to facilitate error categorization and
        systematic error handling across the workflow system.

        :return: Integer error code for programmatic identification and categorization
        """
        return self.value[0]

    @property
    def msg(self) -> str:
        """
        Get the human-readable error message.

        Returns the descriptive error message that provides clear information
        about the error for user feedback, logging, and debugging purposes.
        The messages are designed to be informative yet user-friendly, helping
        both developers and end-users understand what went wrong.

        :return: Descriptive error message for user feedback and system logging
        """
        return self.value[1]
