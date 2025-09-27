from contextlib import contextmanager

from loguru import logger
from sqlalchemy import Engine, create_engine
from sqlmodel import Session

from common.service.base import Service, ServiceType


class DatabaseService(Service):
    name = ServiceType.DATABASE_SERVICE

    def __init__(
        self,
        database_url: str,
        connect_timeout: int = 10,
        pool_size: int = 200,
        max_overflow: int = 800,
        pool_recycle: int = 3600,
    ):
        """
        :param database_url:    数据连接地址
        :param connect_timeout: 超时时间
        :param pool_size:       连接池大小
        :param max_overflow:    额外连接数
        :param pool_recycle:    重用连接之前的最大秒数，用于处理数据库服务器自动关闭长时间运行的连接的情况
        """
        self.database_url = database_url
        self.connect_timeout = connect_timeout
        self.pool_size = pool_size
        self.max_overflow = max_overflow
        self.pool_recycle = pool_recycle
        self.engine = self._create_engine()
        logger.debug("database init success")

    def _create_engine(self) -> "Engine":
        """Create the engine for the database."""

        return create_engine(
            self.database_url,
            echo=False,
            pool_size=self.pool_size,
            max_overflow=self.max_overflow,
            pool_recycle=self.pool_recycle,
        )

    def __enter__(self):  # type: ignore[no-untyped-def]
        self._session = Session(self.engine)
        return self._session

    def __exit__(self, exc_type, exc_value, traceback):  # type: ignore[no-untyped-def]
        if exc_type is not None:  # If an exception has been raised
            logger.error(
                f"Session rollback because of exception: {exc_type.__name__} {exc_value}"
            )
            self._session.rollback()
        else:
            self._session.commit()
        self._session.close()

    def get_session(self):  # type: ignore[no-untyped-def]
        with Session(self.engine) as session:
            yield session


@contextmanager
def session_getter(db_service: "DatabaseService"):  # type: ignore[no-untyped-def]
    try:
        session = Session(db_service.engine)
        yield session
    except Exception as e:
        # print("Session rollback because of exception:", e)
        logger.debug(f"Session rollback because of exception: {e}")
        session.rollback()
        raise
    finally:
        session.close()
