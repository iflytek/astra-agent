"""
Data Access Object (DAO) for flow-related database operations.

This module provides functions to interact with the flow table in the database,
handling flow retrieval and data transformation operations.
"""

import json

from sqlalchemy import text
from sqlmodel import Session  # type: ignore

from workflow.domain.models.flow import Flow


def get_latest_published_flow_by(
    flow_group_id: str, session: Session, version: str = ""
) -> Flow | None:
    """
    Retrieve the latest published flow by group ID and optional version.

    This function queries the database for the most recent published flow
    based on the flow group ID. It supports filtering by specific version
    and orders results by semantic versioning (major.minor format).

    :param flow_group_id: The unique identifier for the flow group
    :param session: Database session for executing queries
    :param version: Optional version filter (e.g., "1.0", "2.1")
    :return: Flow object if found, None otherwise
    """
    # Build WHERE clause for published flows (release_status bitwise check)
    sql_where = "group_id = :group_id " "AND (release_status & :release_status) > 0 "
    if version:
        sql_where += "AND version = :version"

    # Construct SQL query with semantic version ordering
    stmt = text(
        f"""
        SELECT *
        FROM flow
        WHERE {sql_where}
        ORDER BY
            -- Major version number (extract from "v1.0" format)
            CAST(
                SUBSTRING_INDEX(SUBSTRING_INDEX(version, '.', 1), 'v', -1) AS SIGNED
            ) DESC,
            -- Minor version number
            CAST(SUBSTRING_INDEX(version, '.', -1) AS SIGNED) DESC
        LIMIT 1;
    """
    )

    # Set query parameters (release_status: 1|4 = published status)
    params = {"group_id": flow_group_id, "release_status": 1 | 4}
    if version:
        params.update({"version": version})

    # Execute query and get first result
    result = session.execute(stmt, params)
    row = result.first()

    if row:
        # Convert database row to Flow object
        flow = Flow(**dict(row._mapping))

        # Parse JSON strings to objects if needed
        if isinstance(flow.data, str):
            flow.data = json.loads(flow.data)
        if isinstance(flow.release_data, str):
            flow.release_data = json.loads(flow.release_data)
        return flow

    return None
