"""Module providing database metadata operations for async database interactions."""

from typing import Any, Optional, Sequence

from memory.database.utils.retry import retry_on_invalid_cached_statement
from sqlalchemy import Row, text
from sqlmodel.ext.asyncio.session import AsyncSession


@retry_on_invalid_cached_statement(max_retries=3)
async def get_id_by_did_uid(
    session: AsyncSession, database_id: int, uid: str
) -> Sequence[Row[Any]]:
    """Get database ID by database ID and user ID.

    Args:
        session: Async database session
        database_id: Database ID to query
        uid: User ID to query

    Returns:
        List of matching database IDs
    """
    db_id = await session.execute(
        text(
            """
            SELECT id FROM database_meta
            WHERE id=:id AND uid=:uid
            """
        ),
        {"id": database_id, "uid": uid},
    )
    return db_id.all()


@retry_on_invalid_cached_statement(max_retries=3)
async def get_id_by_did(session: AsyncSession, database_id: int) -> Sequence[Row[Any]]:
    """Get database ID by database ID only.

    Args:
        session: Async database session
        database_id: Database ID to query

    Returns:
        List of matching database IDs
    """
    db_id = await session.execute(
        text(
            """
            SELECT id FROM database_meta
            WHERE id=:id
            """
        ),
        {"id": database_id},
    )
    return db_id.all()


@retry_on_invalid_cached_statement(max_retries=3)
async def del_database_meta_by_did(session: AsyncSession, database_id: int) -> None:
    """Delete database metadata by database ID.

    Args:
        session: Async database session
        database_id: Database ID to delete
    """
    await session.execute(
        text(
            """
            DELETE FROM database_meta WHERE id=:database_id;
            """
        ),
        {"database_id": database_id},
    )


@retry_on_invalid_cached_statement(max_retries=3)
async def update_database_meta_by_did_uid(
    session: AsyncSession, database_id: int, uid: str, description: str
) -> None:
    """Update database description by database ID and user ID.

    Args:
        session: Async database session
        database_id: Database ID to update
        uid: User ID to verify
        description: New description to set
    """
    await session.execute(
        text(
            """
            UPDATE database_meta
            SET description=:description
            WHERE id=:database_id AND uid=:uid
            """
        ),
        {"description": description, "database_id": database_id, "uid": uid},
    )


@retry_on_invalid_cached_statement(max_retries=3)
async def get_uid_by_did_space_id(
    session: AsyncSession, database_id: int, space_id: str
) -> Sequence[Row[Any]]:
    """Get user ID by database ID and space ID.

    Args:
        session: Async database session
        database_id: Database ID to query
        space_id: Space ID to query

    Returns:
        List of matching user IDs
    """
    uid = await session.execute(
        text(
            """
            SELECT uid FROM database_meta
            WHERE id=:id AND space_id=:space_id
            """
        ),
        {"id": database_id, "space_id": space_id},
    )
    return uid.all()


@retry_on_invalid_cached_statement(max_retries=3)
async def get_uid_by_space_id(session: AsyncSession, space_id: str) -> Optional[Any]:
    """Get user ID by space ID only.

    Args:
        session: Async database session
        space_id: Space ID to query

    Returns:
        First matching user ID or None
    """
    uid = await session.execute(
        text(
            """
            SELECT uid FROM database_meta
            WHERE space_id=:space_id
            """
        ),
        {"space_id": space_id},
    )
    return uid.first()
