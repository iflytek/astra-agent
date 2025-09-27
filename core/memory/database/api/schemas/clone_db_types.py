"""Clone database schema definitions.

This module contains Pydantic models for clone database operation input validation.
"""

from memory.database.api.schemas.common_types import DidUidCommon


class CloneDBInput(DidUidCommon):  # pylint: disable=too-few-public-methods
    """Input model for cloning a database.

    Attributes:
        database_id: The ID of the database to clone (required)
        uid: User ID (required, 1-64 chars, no Chinese or special characters)
        new_database_name: Name for the new cloned database (required)
    """

    # new_database_name: Required
    new_database_name: str
