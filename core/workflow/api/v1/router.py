"""
Main router configuration for workflow API v1.

This module sets up the main API router and includes all sub-routers
for different API endpoints including chat, flow management, and debugging.
"""

from fastapi import APIRouter

from workflow.api.v1.chat import (
    node_debug_router,
    sse_debug_chat_router,
    sse_openapi_router,
)
from workflow.api.v1.flow import file_router, layout_router
from workflow.api.v1.flow.publish_auth import publish_auth_router

# Main workflow router with v1 prefix
workflow_router = APIRouter(prefix="/workflow/v1")

# Include all sub-routers
workflow_router.include_router(layout_router)
workflow_router.include_router(publish_auth_router)
workflow_router.include_router(node_debug_router)
workflow_router.include_router(file_router)
workflow_router.include_router(sse_debug_chat_router)
workflow_router.include_router(sse_openapi_router)


# Legacy interface compatibility router
sparkflow_router = APIRouter(
    prefix="/sparkflow/v1",
)
sparkflow_router.include_router(node_debug_router)
sparkflow_router.include_router(layout_router)
