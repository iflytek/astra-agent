"""HTTP tool execution API endpoints.

This module provides API endpoints for executing and debugging HTTP tools,
allowing users to run HTTP requests and debug tool functionality.
"""

from fastapi import APIRouter, Body
from plugin.link.api.schemas.community.tools.http.execution_schema import (
    HttpRunRequest,
    HttpRunResponse,
    ToolDebugRequest,
    ToolDebugResponse,
)
from plugin.link.service.community.tools.http.execution_server import (
    http_run,
    tool_debug,
)

# HTTP tool execution router
execution_router = APIRouter(tags=["http tool execution api"])


@execution_router.post("/tools/http_run")
async def http_run_api(run_params: HttpRunRequest = Body()) -> HttpRunResponse:
    """
    HTTP tool execution
    """
    return await http_run(run_params=run_params)


@execution_router.post("/tools/tool_debug")
async def tool_debug_api(
    tool_debug_params: ToolDebugRequest = Body(),
) -> ToolDebugResponse:
    """
    HTTP tool debugging
    """
    return await tool_debug(tool_debug_params=tool_debug_params)
