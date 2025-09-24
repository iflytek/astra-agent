"""Deprecated tool management API endpoints.

This module contains deprecated API endpoints for tool management operations.
These endpoints are no longer maintained and will be removed in future versions.
Please use the versioned tool management APIs instead.
"""

from fastapi import APIRouter, Query
from plugin.link.api.schemas.community.deprecated.management_schema import (
    ToolManagerRequest,
)
from plugin.link.service.community.deprecated.management_server import (
    create_tools,
    delete_tools,
    read_tools,
    update_tools,
)

deprecated_router = APIRouter(tags=["deprecated tool management api"])


@deprecated_router.post(
    "/tools",
    deprecated=True,
    summary="Deprecated: No longer maintained api, please use "
    "`/tools/version` instead.",
)
def create_tools_api(tools_info: ToolManagerRequest):
    """Create new tools using deprecated API.

    Args:
        tools_info: Tool creation request data

    Returns:
        Tool creation response
    """
    return create_tools(tools_info=tools_info)


@deprecated_router.delete(
    "/tools",
    deprecated=True,
    summary="Deprecated: No longer maintained api, please use "
    "`/tools/version` instead.",
)
def delete_tools_api(tool_ids: list[str] = Query(), app_id: str = Query()):
    """Delete existing tools using deprecated API.

    Args:
        tool_ids: List of tool IDs to delete
        app_id: Application ID

    Returns:
        Tool deletion response
    """
    return delete_tools(tool_ids=tool_ids, app_id=app_id)


@deprecated_router.put(
    "/tools",
    deprecated=True,
    summary="Deprecated: No longer maintained api, please use "
    "`/tools/version` instead.",
)
def update_tools_api(tools_info: ToolManagerRequest):
    """Update existing tools using deprecated API.

    Args:
        tools_info: Tool update request data

    Returns:
        Tool update response
    """
    return update_tools(tools_info=tools_info)


@deprecated_router.get(
    "/tools",
    deprecated=True,
    summary="Deprecated: No longer maintained api, please use "
    "`/tools/version` instead.",
)
def read_tools_api(tool_ids: list[str] = Query(), app_id: str = Query()):
    """Retrieve tool information using deprecated API.

    Args:
        tool_ids: List of tool IDs to retrieve
        app_id: Application ID

    Returns:
        Tool information response
    """
    return read_tools(tool_ids=tool_ids, app_id=app_id)
