"""
Data Access Object (DAO) for license-related database operations.

This module provides functions to interact with the license table in the database,
handling license retrieval operations through app and flow group relationships.
"""

from sqlalchemy import text
from sqlmodel import Session  # type: ignore
from workflow.domain.models.license import License


def get_by(flow_group_id: str, app_alias_id: str, session: Session) -> License | None:
    """
    Retrieve license information by flow group ID and app alias ID.

    This function performs a JOIN operation between the app and license tables
    to find the license associated with a specific app and flow group.

    :param flow_group_id: The unique identifier for the flow group
    :param app_alias_id: The alias identifier for the application
    :param session: Database session for executing queries
    :return: License object if found, None otherwise
    """
    # Execute JOIN query to find license by app alias and flow group
    result = session.execute(
        text(
            """
                SELECT license.*
                FROM app
                JOIN license ON app.id = license.app_id
                WHERE app.alias_id = :alias_id AND license.group_id = :group_id
                LIMIT 1;
            """
        ),
        {"alias_id": app_alias_id, "group_id": flow_group_id},
    )

    # Get the first (and only) result row
    row = result.first()
    if row:
        # Convert database row to License object
        return License(**dict(row._mapping))
    return None
