"""Drop database schema definitions.

This module contains Pydantic models for drop database operation input validation.
"""

from typing import Optional

from memory.database.api.schemas.common_types import DidUidCommon
from pydantic import Field


class DropDBInput(DidUidCommon):  # pylint: disable=too-few-public-methods
    """Input model for dropping a database.

    Attributes:
        database_id: The ID of the database to drop (required)
        uid: User ID (required, 1-64 chars, no Chinese or special characters)
        space_id: Optional team space ID
    """

    # space_id: Optional
    space_id: Optional[str] = Field(default="", description="Team space ID")
