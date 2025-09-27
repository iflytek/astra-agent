"""DDL Execution Schema Definitions.

This module contains the Pydantic model for validating DDL (Data Definition Language)
execution requests. It defines the required and optional fields with their constraints.
"""

from typing import Optional

from memory.database.api.schemas.common_types import DidUidCommon
from pydantic import Field


class ExecDDLInput(DidUidCommon):  # pylint: disable=too-few-public-methods
    """Input validation model for executing DDL statements.

    Attributes:
        database_id (int): Target database ID (required)
        uid (str): User ID (required, 1-64 chars, no Chinese/special characters)
        ddl (str): DDL statement to execute (required, min length 1)
        space_id (Optional[str]): Team space ID (optional)
    """

    # ddl: Required, minimum length 1
    ddl: str = Field(..., min_length=1, description="Required, minimum length 1")
    # space_id: Optional
    space_id: Optional[str] = Field(default="", description="Team space ID")
