"""Module providing schema metadata operations for database schemas."""

from typing import Any, Sequence

from memory.database.utils.retry import retry_on_invalid_cached_statement
from sqlalchemy import Row, text
from sqlmodel.ext.asyncio.session import AsyncSession


@retry_on_invalid_cached_statement(max_retries=3)
async def get_schema_name_by_did(
    session: AsyncSession, database_id: int
) -> Sequence[Row[Any]]:
    """Retrieve schema names associated with a database ID.

    Args:
        session: Async database session
        database_id: ID of the database to query schemas for

    Returns:
        List of schema names associated with the database
    """
    prod_test_schemas = await session.execute(
        text(
            """
            SELECT schema_name FROM schema_meta
            WHERE database_id=:database_id
            """
        ),
        {"database_id": database_id},
    )
    return prod_test_schemas.all()


@retry_on_invalid_cached_statement(max_retries=3)
async def del_schema_meta_by_did(session: AsyncSession, database_id: int) -> None:
    """Delete all schema metadata entries for a given database ID.

    Args:
        session: Async database session
        database_id: ID of the database whose schemas should be deleted
    """
    await session.execute(
        text(
            """
            DELETE FROM schema_meta WHERE database_id=:database_id;
            """
        ),
        {"database_id": database_id},
    )
