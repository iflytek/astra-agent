"""MCP (Model Context Protocol) tools data transfer objects.

This module contains Pydantic models for MCP tool operations including
tool listing and tool execution requests and responses.
"""

from typing import Any

from pydantic import BaseModel


# MCPToolList Request and Response
class MCPToolListRequest(BaseModel):
    """Request model for listing available MCP tools from servers.

    Allows filtering by specific server IDs or URLs to get tools from
    particular MCP servers.
    """

    mcp_server_ids: list[str] | None = None
    mcp_server_urls: list[str] | None = None


class MCPInfo(BaseModel):
    """Information about an individual MCP tool.

    Contains the tool's name, description, and input schema definition.
    """

    name: str
    description: str | None = None
    inputSchema: Any | None = None


class MCPItemInfo(BaseModel):
    """Information about an MCP server and its available tools.

    Includes server identification, status, and the list of tools
    available from that server.
    """

    server_id: str | None = None
    server_url: str | None = None
    server_status: int
    server_message: str
    tools: list[MCPInfo] | None = None


class MCPToolListData(BaseModel):
    """Data payload for MCP tool list response.

    Contains the list of MCP servers and their tool information.
    """

    servers: list[MCPItemInfo] | None = None


class MCPToolListResponse(BaseModel):
    """Complete response for MCP tool listing requests.

    Standard API response format with code, message, session ID,
    and the tool list data payload.
    """

    code: int
    message: str
    sid: str
    data: MCPToolListData


# MCPCallTool Request and Response
class MCPCallToolRequest(BaseModel):
    """Request model for calling/executing an MCP tool.

    Specifies the target server, tool name, and arguments for
    tool execution.
    """

    mcp_server_id: str | None = None
    mcp_server_url: str | None = None
    tool_name: str
    tool_args: dict[str, Any] | None = None


class MCPTextResponse(BaseModel):
    """Text content response from MCP tool execution.

    Represents text-based output from an MCP tool call.
    """

    type: str = "text"
    text: str


class MCPImageResponse(BaseModel):
    """Image content response from MCP tool execution.

    Represents image-based output from an MCP tool call with
    base64 encoded data and MIME type.
    """

    type: str = "image"
    data: str
    mineType: str


class MCPCallToolData(BaseModel):
    """Data payload for MCP tool execution response.

    Contains execution status and content (text or image responses)
    from the tool call.
    """

    isError: bool | None = None
    content: list[MCPTextResponse | MCPImageResponse] | None = None


class MCPCallToolResponse(BaseModel):
    """Complete response for MCP tool execution requests.

    Standard API response format with code, message, session ID,
    and the tool execution data payload.
    """

    code: int
    message: str
    sid: str
    data: MCPCallToolData
