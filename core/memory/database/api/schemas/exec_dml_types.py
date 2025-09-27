"""DML Execution Schema Definitions.

This module contains the Pydantic model for validating DML (Data Manipulation Language)
execution requests. It defines the required and optional fields with their constraints
for executing database modification operations.
"""

from typing import Literal, Optional

from memory.database.api.schemas.common_types import DidUidCommon
from pydantic import Field


class ExecDMLInput(DidUidCommon):  # pylint: disable=too-few-public-methods
    """Input validation model for executing DML statements.

    Attributes:
        app_id (str): Application ID (required, no Chinese/special characters)
        database_id (int): Target database ID (required)
        uid (str): User ID (required, 1-64 chars, no Chinese/special characters)
        dml (str): DML statement to execute (required)
        env (Literal["prod", "test"]): Environment (required, either 'prod' or 'test')
        space_id (Optional[str]): Team space ID (optional)
    """

    # app_id: Required, cannot contain Chinese and special characters
    app_id: str = Field(
        ...,
        pattern=r"^$|^[^！@#￥%……&*()\u4e00-\u9fa5]+$",
        description="Required, cannot contain Chinese and special symbols！@#￥%……&*()",
    )
    # dml: Required
    dml: str
    # env: Required, can only be prod or test
    env: Literal["prod", "test"] = Field(
        ..., description="Required, can only be prod or test"
    )
    # space_id: Optional
    space_id: Optional[str] = Field(default="", description="Team space ID")
