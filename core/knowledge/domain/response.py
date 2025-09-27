"""
API response class module
Defines standard API response formats, including success responses and error responses
"""

from typing import Any, Optional

from pydantic import BaseModel, ConfigDict

from knowledge.consts.error_code import CodeEnum


class BaseResponse(BaseModel):
    """
    Base API response class that encapsulates common response properties and logic.
    """

    code: int
    message: str
    sid: Optional[str] = None

    # Pydantic V2 configuration (replaces previous orm_mode = True)
    model_config = ConfigDict(
        from_attributes=True,  # Allow serialization from object attributes
        arbitrary_types_allowed=True,  # Allow arbitrary types of data fields
    )

    def is_success(self) -> bool:
        """Check if response is successful"""
        return self.code == 0

    def to_dict(self) -> dict:
        """Convert response to dictionary, excluding None fields"""
        return self.model_dump(exclude_none=True)


class SuccessDataResponse(BaseResponse):
    """Success response (with data)"""

    data: Optional[Any] = None

    def __init__(self, data: Any, message: str = "success", sid: Optional[str] = None):
        super().__init__(code=0, message=message, sid=sid)
        self.data = data


class ErrorResponse(BaseResponse):
    """Error response"""

    def __init__(
        self,
        code_enum: CodeEnum,
        sid: Optional[str] = None,
        message: Optional[str] = None,
    ) -> None:
        # If message parameter is provided, use it; otherwise use code_enum's msg
        msg = message if message is not None else code_enum.msg
        super().__init__(code=code_enum.code, message=msg, sid=sid)
