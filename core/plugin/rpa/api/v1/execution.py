"""RPA execution related API routes."""

import json
import os
from datetime import datetime, timezone

from fastapi import APIRouter, Body, Header, HTTPException
from plugin.rpa.api.schemas.execution_schema import RPAExecutionRequest
from plugin.rpa.consts import const
from plugin.rpa.service.xiaowu.process import task_monitoring
from sse_starlette.sse import EventSourceResponse

# RPA execution router
execution_router = APIRouter(tags=["rpa execution api"])


@execution_router.post("/exec")
async def exec_fun(
    Authorization: str = Header(  # pylint: disable=invalid-name
        ..., description="Access token"
    ),
    request: RPAExecutionRequest = Body(
        ..., description="RPA execution request parameters"
    ),
) -> EventSourceResponse:
    """Execute RPA task and return streaming response."""
    try:
        # Set response headers
        headers = {
            "Content-Type": "text/event-stream; charset=utf-8",
            "Transfer-Encoding": "chunked",
            "Connection": "keep-alive",
            "Date": datetime.now(timezone.utc).strftime("%a, %d %b %Y %H:%M:%S GMT"),
            "Cache-Control": "no-cache, no-transform",
            "X-Accel-Buffering": "no",
        }
        ping_interval = int(os.getenv(const.XIAOWU_RPA_PING_INTERVAL_KEY, "3"))
        acctss_token = (
            Authorization[7:] if Authorization.startswith("Bearer ") else Authorization
        )
        return EventSourceResponse(
            task_monitoring(
                sid=request.sid,
                access_token=acctss_token,
                project_id=request.project_id,
                exec_position=request.exec_position,
                params=request.params,
            ),
            headers=headers,
            ping=ping_interval,
        )
    except json.JSONDecodeError as e:
        raise HTTPException(
            status_code=400, detail=f"Invalid JSON format for 'params':" f" {e}"
        ) from e
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e)) from e
