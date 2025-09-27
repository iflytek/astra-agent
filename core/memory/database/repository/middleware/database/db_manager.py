"""
Database service manager module for handling async database connections and sessions.
"""

from typing import AsyncGenerator, Optional

from loguru import logger
from memory.database.repository.middleware.base import Service
from memory.database.repository.middleware.mid_utils import ServiceType
from sqlalchemy.exc import InterfaceError
from sqlalchemy.ext.asyncio import AsyncEngine, create_async_engine
from sqlalchemy.orm import sessionmaker
from sqlmodel import SQLModel
from sqlmodel.ext.asyncio.session import AsyncSession


class DatabaseService(Service):
    """Database service class for managing async database connections.

    Attributes:
        name: Service type identifier
        database_url: Database connection URL
        connect_timeout: Connection timeout in seconds
        pool_size: Connection pool size
        max_overflow: Maximum overflow connections
        pool_recycle: Connection recycle time in seconds
        engine: Async SQLAlchemy engine
        _async_session: Async session factory
    """

    name = ServiceType.DATABASE_SERVICE

    def __init__(
        self,
        database_url: str,
        connect_timeout: int = 10,
        pool_size: int = 200,
        max_overflow: int = 800,
        pool_recycle: int = 3600,
    ):
        """Initialize database service with connection parameters.

        Args:
            database_url: Database connection URL
            connect_timeout: Connection timeout in seconds
            pool_size: Connection pool size
            max_overflow: Maximum overflow connections
            pool_recycle: Connection recycle time in seconds
        """
        self.database_url = database_url
        self.connect_timeout = connect_timeout
        self.pool_size = pool_size
        self.max_overflow = max_overflow
        self.pool_recycle = pool_recycle
        self.engine: Optional[AsyncEngine] = None
        self._async_session: Optional[sessionmaker] = None

    @classmethod
    async def create(
        cls,
        database_url: str,
        connect_timeout: int = 10,
        pool_size: int = 200,
        max_overflow: int = 800,
        pool_recycle: int = 3600,
    ) -> "DatabaseService":
        """Create and initialize database service instance.

        Args:
            database_url: Database connection URL
            connect_timeout: Connection timeout in seconds
            pool_size: Connection pool size
            max_overflow: Maximum overflow connections
            pool_recycle: Connection recycle time in seconds

        Returns:
            Initialized DatabaseService instance
        """
        self = cls(database_url, connect_timeout, pool_size, max_overflow, pool_recycle)
        self.engine = await self._create_engine()
        self._async_session = sessionmaker(  # type: ignore[call-overload]
            self.engine, class_=AsyncSession, expire_on_commit=False
        )
        logger.debug("database init success")
        return self

    async def _create_engine(self) -> AsyncEngine:
        """Create async SQLAlchemy engine with configured parameters.

        Returns:
            AsyncEngine: Configured async database engine
        """
        return create_async_engine(
            self.database_url,
            echo=False,
            pool_size=self.pool_size,
            max_overflow=self.max_overflow,
            pool_recycle=self.pool_recycle,
            pool_pre_ping=True,
            connect_args={"statement_cache_size": 0},  # Disable asyncpg statement cache
        )

    async def init_db(self) -> None:
        """Initialize database by creating all tables."""
        if self.engine is None:
            raise RuntimeError("Database engine not initialized")
        async with self.engine.begin() as conn:
            await conn.run_sync(SQLModel.metadata.create_all)

    async def get_session(self) -> AsyncGenerator[AsyncSession, None]:
        """Get an async database session with transaction management.

        Yields:
            AsyncSession: Database session instance

        Raises:
            InterfaceError: On database interface errors
            Exception: On other errors with rollback
        """
        if self._async_session is None:
            raise RuntimeError("Database service not properly initialized")
        async with self._async_session() as session:
            try:
                yield session
                await session.commit()
            except InterfaceError as e:
                logger.error(f"Database interface error: {e}")
                await session.rollback()
                raise
            except Exception as e:
                await session.rollback()
                logger.error(f"Session rollback due to: {e}")
                raise
