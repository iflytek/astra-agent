"""Create database schema definitions.

This module contains Pydantic models for create database operation input validation.
"""

from typing import Optional

from memory.database.api.schemas.common_types import UidCommon
from pydantic import Field


class CreateDBInput(UidCommon):  # pylint: disable=too-few-public-methods
    """Input model for creating a new database.

    Attributes:
        database_name: Database name (required, 1-20 chars, starts with letter,
                       only letters, numbers and underscores)
        uid: User ID (required, 1-64 chars, no Chinese or special characters)
        description: Optional description (max 200 chars)
        space_id: Optional team space ID
    """

    # database_name: Required, length 1-20, regex restriction
    database_name: str = Field(
        ...,
        min_length=1,
        max_length=20,
        pattern=r"^[a-zA-Z][a-zA-Z0-9_]{0,19}$",
        description="Required, starts with letter, contains only letters, "
        "numbers and underscores, max 20 characters",
    )
    # description: Optional, max 200 characters
    description: Optional[str] = Field(
        default=None, max_length=200, description="Optional, max 200 characters"
    )
    # space_id: Optional
    space_id: Optional[str] = Field(default="", description="Team space ID")
