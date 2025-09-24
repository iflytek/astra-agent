"""Error code definitions module for system-wide error handling.

Defines standardized error codes and messages for various system
components including initialization, validation, API calls, and MCP operations.
"""

from enum import Enum


class ErrCode(Enum):
    """
    Error codes for system-wide error handling.
    """

    SUCCESSES = (0, "Success")
    APP_INIT_ERR = (30001, "Initialization failed")
    COMMON_ERR = (30100, "General error")

    JSON_PROTOCOL_PARSER_ERR = (30200, "JSON protocol parsing failed")
    JSON_SCHEMA_VALIDATE_ERR = (30201, "Protocol validation failed")
    RESPONSE_SCHEMA_VALIDATE_ERR = (
        30202,
        "Response type does not match tool configuration",
    )
    SERVER_VALIDATE_ERR = (30203, "Tool request hostname is blacklisted")
    APP_ID_VALIDATE_ERR = (30204, "App ID validation failed")

    OPENAPI_SCHEMA_VALIDATE_ERR = (30300, "OpenAPI protocol parsing failed")
    OPENAPI_SCHEMA_BODY_TYPE_ERR = (30301, "Body type not supported")
    OPENAPI_SCHEMA_SERVER_NOT_EXIST_ERR = (30302, "Server does not exist")
    OPENAPI_AUTH_TYPE_ERR = (30303, "Authentication type mismatch in protocol")

    OFFICIAL_API_REQUEST_FAILED_ERR = (30400, "Official API request failed")
    FUNCTION_CALL_FAILED_ERR = (30401, "Function call failed")
    LLM_CALL_FAILED_ERR = (30402, "LLM call failed")
    THIRD_API_REQUEST_FAILED_ERR = (30403, "Third-party API request failed")

    TOOL_NOT_EXIST_ERR = (30500, "Tool does not exist")
    VERSION_NOT_EXIST_ERR = (30501, "Version does not exist")
    VERSION_NOT_ASSIGN_ERR = (30502, "Version not specified")

    OPERATION_ID_NOT_EXIST_ERR = (30600, "Operation does not exist")

    MCP_SERVER_ID_EMPTY_ERR = (30700, "MCP server ID is empty")
    MCP_CRUD_OPERATION_FAILED_ERR = (30701, "MCP database operation failed")
    MCP_SERVER_NOT_FOUND_ERR = (30702, "No matching MCP server information found")
    MCP_SERVER_CONNECT_ERR = (30703, "MCP client server connection failed")
    MCP_SERVER_SESSION_ERR = (30704, "MCP client session creation failed")
    MCP_SERVER_INITIAL_ERR = (30705, "MCP client initialization failed")
    MCP_SERVER_TOOL_LIST_ERR = (30706, "MCP client failed to retrieve tool list")
    MCP_SERVER_CALL_TOOL_ERR = (30707, "MCP client tool call failed")
    MCP_SERVER_URL_EMPTY_ERR = (30708, "MCP server URL is empty")
    MCP_SERVER_LOCAL_URL_ERR = (30709, "MCP server is loopback address")
    MCP_SERVER_BLACKLIST_URL_ERR = (30710, "MCP server URL is blacklisted")

    @property
    def code(self):
        """Get status code"""
        return self.value[0]

    @property
    def msg(self):
        """Get status code message"""
        return self.value[1]
