"""
API Router Configuration Module

This module configures the main API router for the Spark Link server, organizing
and including various sub-routers for different functionalities including:
- HTTP tools management and execution
- MCP (Model Context Protocol) tools
- Deprecated API endpoints for backward compatibility
- Enterprise extension features
"""

from fastapi import APIRouter
from plugin.link.api.v1.community.deprecated.management import deprecated_router
from plugin.link.api.v1.community.tools.http.execution import execution_router
from plugin.link.api.v1.community.tools.http.management import management_router
from plugin.link.api.v1.community.tools.mcp.mcp_tools import mcp_router
from plugin.link.api.v1.enterprise.extension import extension_router

# root
router = APIRouter(
    prefix="/api/v1",
)

# http tool
router.include_router(management_router)
router.include_router(execution_router)

# mcp tool
router.include_router(mcp_router)

# old version http tool management: deprecated
router.include_router(deprecated_router)

# enterprise version enhanced features
router.include_router(extension_router)
