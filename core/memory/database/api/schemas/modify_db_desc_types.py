"""Database Description Modification Schema Definitions.

This module contains the Pydantic model for validating database description
modification requests. It defines the required and optional fields with their
constraints for updating database descriptions.
"""

from typing import Optional

from memory.database.api.schemas.common_types import DidUidCommon
from pydantic import Field


class ModifyDBDescInput(DidUidCommon):  # pylint: disable=too-few-public-methods
    """Input validation model for modifying database descriptions.

    Attributes:
        database_id (int): Target database ID (required)
        uid (str): User ID (required, 1-64 chars, no Chinese/special characters)
        description (Optional[str]): New description (optional, max 200 chars)
        space_id (Optional[str]): Team space ID (optional)
    """

    # description: Optional, max 200 characters
    description: Optional[str] = Field(
        default=None, max_length=200, description="Optional, max 200 characters"
    )
    # space_id: Optional
    space_id: Optional[str] = Field(default="", description="Team space ID")
