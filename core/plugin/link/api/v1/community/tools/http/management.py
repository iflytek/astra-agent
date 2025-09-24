"""HTTP tool management API endpoints.

This module provides API endpoints for managing HTTP tools including
creating, reading, updating, and deleting HTTP tool versions.
"""

from fastapi import APIRouter, Body, Query
from plugin.link.api.schemas.community.tools.http.management_schema import (
    ToolCreateRequest,
    ToolManagerResponse,
    ToolUpdateRequest,
)
from plugin.link.service.community.tools.http.management_server import (
    create_version,
    delete_version,
    read_version,
    update_version,
)

# HTTP tool management router
management_router = APIRouter(tags=["http tool management api"])


@management_router.post("/tools/versions")
def create_version_api(tools_info: ToolCreateRequest = Body()) -> ToolManagerResponse:
    """
    Add a new tool.
    """
    return create_version(tools_info=tools_info)


@management_router.delete("/tools/versions")
def delete_version_api(
    app_id: str = Query(),
    tool_ids: list[str] = Query(),
    versions: list[str] = Query(default=None),
) -> ToolManagerResponse:
    """
    Delete existing tool.
    """
    return delete_version(tool_ids=tool_ids, app_id=app_id, versions=versions)


@management_router.put("/tools/versions")
def update_version_api(tools_info: ToolUpdateRequest = Body()) -> ToolManagerResponse:
    """
    Update existing tool.
    """
    return update_version(tools_info=tools_info)


@management_router.get("/tools/versions")
def read_version_api(
    app_id: str = Query(), tool_ids: list[str] = Query(), versions: list[str] = Query()
) -> ToolManagerResponse:
    """
    Search for existing tool.
    """
    return read_version(tool_ids=tool_ids, app_id=app_id, versions=versions)
