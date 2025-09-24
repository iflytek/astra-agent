"""Enterprise extension API endpoints.

This module provides API endpoints for enterprise-only features and extensions,
including MCP tool registration and other advanced functionality.
"""

from fastapi import APIRouter, Body
from plugin.link.api.schemas.enterprise.extension_schema import (
    MCPManagerRequest,
    MCPManagerResponse,
)
from plugin.link.service.enterprise.extension import register_mcp

# Enterprise extension features
extension_router = APIRouter(tags=["extension api: enterprise version only"])


@extension_router.post("/mcp")
def register_mcp_api(mcp_info: MCPManagerRequest = Body()) -> MCPManagerResponse:
    """
    Register MCP tool
    """
    return register_mcp(mcp_info=mcp_info)
