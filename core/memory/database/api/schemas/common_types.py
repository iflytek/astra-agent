"""Common database schema definitions.

This module contains Pydantic models for clone database operation input validation.
"""

from pydantic import BaseModel, Field


class UidCommon(BaseModel):  # pylint: disable=too-few-public-methods
    """Common base model with user ID validation.

    Attributes:
        uid: User ID (required, 1-64 chars, no Chinese or special characters)
    """

    # uid: Required, length 1-64, cannot contain Chinese and special characters
    uid: str = Field(
        ...,
        min_length=1,
        max_length=64,
        pattern=r"^[^！@#￥%……&*()\u4e00-\u9fa5]+$",
        description="Required, length 1-64, cannot contain Chinese "
        "and special symbols！@#￥%……&*()",
    )


class DidUidCommon(UidCommon):  # pylint: disable=too-few-public-methods
    """Input model for dropping a database.

    Attributes:
        database_id: The ID of the database to drop (required)
        uid: User ID (required, 1-64 chars, no Chinese or special characters)
    """

    # database_id: Required
    database_id: int = Field(..., strict=True)
