"""DTO definition module for RPA execution requests and responses.
This module defines data transfer objects (DTOs) related to RPA execution requests and responses.
"""

from typing import Any, Dict, Optional

from pydantic import BaseModel


class RPAExecutionRequest(BaseModel):
    """DTO definition for RPA execution request."""

    sid: Optional[str] = ""
    project_id: str
    exec_position: Optional[str] = "EXECUTOR"
    params: Optional[Dict[Any, Any]] = None


class RPAExecutionResponse(BaseModel):
    """DTO definition for RPA execution response."""

    code: int
    message: str
    sid: Optional[str] = ""
    data: Optional[Dict[Any, Any]] = None
