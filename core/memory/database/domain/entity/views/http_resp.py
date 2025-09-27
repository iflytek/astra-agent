"""Module providing standardized HTTP response formats for API endpoints."""

from typing import Any, Optional

from starlette.responses import JSONResponse


class SuccessResponse:  # pylint: disable=too-few-public-methods
    """Basic success response format without data payload.

    Attributes:
        code: Status code (0 for success)
        message: Human-readable message
        sid: Optional session identifier
    """

    code: int
    message: str

    def __init__(self, message: str = "success", sid: Optional[str] = None) -> None:
        """Initialize success response.

        Args:
            message: Optional success message
            sid: Optional session identifier
        """
        self.code = 0
        self.message = message
        if sid is not None:
            self.sid = sid


class SuccessDataResponse:  # pylint: disable=too-few-public-methods
    """Success response format with data payload.

    Attributes:
        code: Status code (0 for success)
        message: Human-readable message
        data: Response payload data
        sid: Optional session identifier
    """

    code: int
    message: str
    data: object

    def __init__(
        self, data: Any, message: str = "success", sid: Optional[str] = None
    ) -> None:
        """Initialize success response with data.

        Args:
            data: JSON-serializable response data
            message: Optional success message
            sid: Optional session identifier
        """
        self.code = 0
        self.data = data
        self.message = message
        if sid is not None:
            self.sid = sid


def format_response(
    code: int, data: Optional[Any] = None, message: str = " ", sid: Optional[str] = None
) -> JSONResponse:
    """Format standardized API response.

    Args:
        code: Status code
        data: Optional response payload data
        message: Human-readable message
        sid: Optional session identifier

    Returns:
        JSONResponse: Formatted Starlette JSON response
    """
    ret = {"code": code, "message": message}
    if data:
        ret["data"] = data
    if sid:
        ret["sid"] = sid
    return JSONResponse(ret, media_type="application/json")
