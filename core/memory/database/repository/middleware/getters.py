"""
Database session management module for async database operations.
"""

from typing import AsyncGenerator

from memory.database.domain.entity.schema import set_search_path_by_schema
from memory.database.repository.middleware.manager import service_manager
from memory.database.repository.middleware.mid_utils import ServiceType
from sqlmodel.ext.asyncio.session import AsyncSession


async def get_session() -> AsyncGenerator[AsyncSession, None]:
    """Get an async database session with configured search path.

    Yields:
        AsyncSession: An async database session instance

    Note:
        Automatically sets the search path to 'sparkdb_manager' schema
        and ensures session is properly closed after use.
    """
    db_service = await service_manager.get(ServiceType.DATABASE_SERVICE)
    async for session in db_service.get_session():  # Manually unpack async generator
        try:
            # Set search_path to admin schema each time a session is obtained
            await set_search_path_by_schema(session, "sparkdb_manager")
            yield session
        finally:
            await session.close()
