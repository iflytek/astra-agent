"""
Third-party API error code definitions.

This module defines error codes returned by various third-party services
that are integrated with the workflow system. These codes represent the
raw error responses from external APIs before they are converted to
internal system error codes for consistent handling.

The error codes in this module are the original error codes returned by
external services and are used as input to the CodeConvert utility for
mapping to internal system error codes. This separation ensures that
external service changes don't directly impact the internal error handling
system.

Supported third-party services:
- Spark AI models and engines (comprehensive error coverage)
- Image generation services (format, schema, and audit errors)
- SparkLink tool services (initialization, protocol, and execution errors)
- Code execution environments (pod readiness and execution failures)
- Content audit services (compliance and violation detection)

Error Code Ranges by Service:
- Code execution: 1, 10405 (pod and execution errors)
- Image generation: 10003-10022 (format, schema, capacity, audit errors)
- SparkLink tools: 30001-30600 (initialization, protocol, execution errors)
- Spark models: 10000-11203 (WebSocket, auth, rate limiting, engine errors)
- Audit services: 999999 (internal audit service errors)

These codes are systematically organized to facilitate error mapping and
provide clear categorization for different types of external service failures.
"""

from enum import Enum


class ThirdApiCodeEnum(Enum):
    """
    Enumeration of third-party API error codes.

    Each error code is defined as a tuple containing:
    - code: Integer error code from third-party service
    - msg: Human-readable error message from the external service

    Error codes are organized by service type with specific ranges:
    - Code execution errors (10405, 1): Pod readiness and execution failures
    - Image generation errors (10003-10022): AI image generation service errors
    - SparkLink tool errors (30001-30600): External tool integration errors
    - Spark model errors (10000-11203): AI model service errors including WebSocket, auth, and rate limiting
    - Audit errors (999999): Content audit service internal errors

    These codes are used as input to the CodeConvert utility for mapping
    to internal system error codes.
    """

    SUCCESS = (0, "Success")

    # Code execution errors
    CODE_EXECUTE_POD_NOT_READY_ERROR = (
        10405,
        "Pod is not ready yet, please try again later",
    )
    CODE_EXECUTE_ERROR = (1, "exec code error::exit status 1")

    # SparkLink related errors
    SPARK_LINK_APP_INIT_ERROR = (30001, "Initialization failed")
    SPARK_LINK_COMMON_ERROR = (30100, "General error")
    SPARK_LINK_JSON_PROTOCOL_PARSER_ERROR = (30200, "JSON protocol parsing failed")
    SPARK_LINK_JSON_SCHEMA_VALIDATE_ERROR = (30201, "Protocol validation failed")
    SPARK_LINK_OPENAPI_SCHEMA_VALIDATE_ERROR = (
        30300,
        "OpenAPI protocol parsing failed",
    )
    SPARK_LINK_OPENAPI_SCHEMA_BODY_TYPE_NOT_SUPPORT_ERROR = (
        30301,
        "Body type not supported",
    )
    SPARK_LINK_OPENAPI_SCHEMA_SERVER_NOT_EXIST_ERROR = (30302, "Server does not exist")
    SPARK_LINK_OFFICIAL_THIRD_API_REQUEST_FAILED_ERROR = (
        30400,
        "Official tool request failed",
    )
    SPARK_LINK_THIRD_API_REQUEST_FAILED_ERROR = (
        30403,
        "Third-party tool request failed",
    )
    SPARK_LINK_TOOL_NOT_EXIST_ERROR = (30500, "Tool does not exist")
    SPARK_LINK_OPERATION_ID_NOT_EXIST_ERROR = (30600, "Operation does not exist")

    # Spark model errors
    SPARK_WS_ERROR = (10000, "WebSocket upgrade error")
    SPARK_WS_READ_ERROR = (10001, "WebSocket read user message error")
    SPARK_WS_SEND_ERROR = (10002, "WebSocket send message to user error")
    SPARK_MESSAGE_FORMAT_ERROR = (10003, "User message format error")
    SPARK_SCHEMA_ERROR = (10004, "User data schema error")
    SPARK_PARAM_ERROR = (10005, "User parameter value error")
    SPARK_CONCURRENCY_ERROR = (
        10006,
        "User concurrency error: current user is connected, same user cannot connect from multiple locations",
    )
    SPARK_TRAFFIC_LIMIT_ERROR = (
        10007,
        "User traffic limit: service is processing user's current question, wait for completion before sending new request",
    )
    SPARK_CAPACITY_ERROR = (10008, "Service capacity insufficient, contact staff")
    SPARK_ENGINE_CONNECTION_ERROR = (10009, "Engine connection establishment failed")
    SPARK_ENGINE_RECEIVE_ERROR = (10010, "Engine data receiving error")
    SPARK_ENGINE_SEND_ERROR = (10011, "Engine data sending error")
    SPARK_ENGINE_INTERNAL_ERROR = (10012, "Engine internal error")
    SPARK_CONTENT_AUDIT_ERROR = (
        10013,
        "Input content audit failed, suspected violation, please adjust input content",
    )
    SPARK_OUTPUT_AUDIT_ERROR = (
        10014,
        "Output content involves sensitive information, audit failed, results cannot be displayed to user",
    )
    SPARK_APP_ID_BLACKLIST_ERROR = (10015, "Appid is in blacklist")
    SPARK_APP_ID_AUTH_ERROR = (
        10016,
        "Appid authorization error: feature not enabled, version not enabled, insufficient tokens, concurrency exceeds authorization",
    )
    SPARK_CLEAR_HISTORY_ERROR = (10017, "Clear history failed")
    SPARK_VIOLATION_ERROR = (
        10019,
        "Session content has violation tendency; suggest showing violation warning to user",
    )
    SPARK_BUSY_ERROR = (10110, "Service busy, please try again later")
    SPARK_ENGINE_PARAMS_ERROR = (
        10163,
        "Engine request parameter error, engine schema check failed",
    )
    SPARK_ENGINE_NETWORK_ERROR = (10222, "Engine network error")
    SPARK_TOKEN_LIMIT_ERROR = (
        10907,
        "Token count exceeds limit, conversation history + question text too long, need to simplify input",
    )
    SPARK_AUTH_ERROR = (
        11200,
        "Authorization error: appId has no feature authorization or business volume exceeds limit",
    )
    SPARK_DAILY_LIMIT_ERROR = (11201, "Authorization error: daily rate limit exceeded")
    SPARK_SECOND_LIMIT_ERROR = (
        11202,
        "Authorization error: second-level rate limit exceeded",
    )
    SPARK_CONCURRENCY_LIMIT_ERROR = (
        11203,
        "Authorization error: concurrency limit exceeded",
    )

    # Audit related errors
    AUDIT_ERROR = (999999, "Server internal error")

    @property
    def code(self) -> int:
        """
        Get the numeric error code from third-party service.

        Returns the original integer error code as returned by the external API.
        This code is used as input to the CodeConvert utility for mapping to
        internal system error codes. The codes are organized by service type
        with specific ranges to facilitate error categorization and mapping.

        :return: Integer error code as returned by the external API
        """
        return self.value[0]

    @property
    def msg(self) -> str:
        """
        Get the error message from third-party service.

        Returns the original error message as returned by the external API.
        This message provides the raw error description from the third-party
        service and is used for reference during error mapping and debugging.
        The messages are typically in the language and format provided by
        the external service.

        :return: Error message as returned by the external API
        """
        return self.value[1]
