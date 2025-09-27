"""Data Export Schema Definitions.

This module contains the Pydantic model for validating data export requests.
It defines the required fields and their constraints for exporting data
from database tables.
"""

from typing import Literal

from memory.database.api.schemas.common_types import DidUidCommon
from pydantic import Field


class ExportDataInput(DidUidCommon):  # pylint: disable=too-few-public-methods
    """Input validation model for exporting data from database tables.

    Attributes:
        app_id (str): Application ID (required, no Chinese/special characters)
        database_id (int): Target database ID (required)
        uid (str): User ID (required, 1-64 chars, no Chinese/special characters)
        table_name (str): Name of the table to export data from (required)
        env (Literal["prod", "test"]): Environment (required, either 'prod' or 'test')
    """

    # app_id: Required, cannot contain Chinese and special characters
    app_id: str = Field(
        ...,
        pattern=r"^$|^[^！@#￥%……&*()\u4e00-\u9fa5]+$",
        description="Required, cannot contain Chinese and special symbols！@#￥%……&*()",
    )
    # table_name: Required
    table_name: str
    # env: Required, can only be prod or test
    env: Literal["prod", "test"] = Field(
        ..., description="Required, can only be prod or test"
    )
