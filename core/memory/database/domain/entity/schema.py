"""Module providing schema-related database operations."""

from memory.database.utils.retry import retry_on_invalid_cached_statement
from sqlalchemy import text
from sqlmodel.ext.asyncio.session import AsyncSession


@retry_on_invalid_cached_statement(max_retries=3)
async def set_search_path_by_schema(session: AsyncSession, schema: str) -> None:
    """Set the database search path to the specified schema.

    Args:
        session: Async database session
        schema: Schema name to set as search path
    """
    await session.exec(text(f'SET search_path = "{schema}"'))  # type: ignore[call-overload]
