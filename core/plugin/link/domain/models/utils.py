"""Database and Redis utility services for the link domain.

This module provides DatabaseService and RedisService classes for managing
database connections and Redis cache operations within the link domain.
Includes session management, table validation, and cache operations.
"""

import json
import os
import re
from contextlib import contextmanager
from dataclasses import dataclass
from typing import TYPE_CHECKING, Dict

import redis
import sqlalchemy as sa
from loguru import logger
from plugin.link.consts import const
from rediscluster import RedisCluster
from sqlalchemy import inspect
from sqlalchemy.exc import OperationalError
from sqlmodel import Session, SQLModel, create_engine

if TYPE_CHECKING:
    from sqlalchemy.engine import Engine


@contextmanager
def session_getter(db_service: "DatabaseService"):
    """Context manager for database session handling.

    Args:
        db_service: DatabaseService instance to create session from.

    Yields:
        Session: SQLAlchemy session object.
    """
    try:
        session = Session(db_service.engine)
        yield session
    except Exception as e:
        print("Session rollback because of exception:", e)
        session.rollback()
        raise
    finally:
        # session.commit()
        session.close()


@dataclass
class Result:
    """Result data class for database operations.

    This class has intentionally few public methods as it serves as a simple
    data container for database operation results. Being a dataclass, it provides:
    - Automatic __init__, __repr__, and __eq__ methods
    - Simple attribute access for operation results
    - Type safety for database validation responses

    Its minimal interface is appropriate as it only needs to store
    and provide access to database operation outcome data.

    Attributes:
        name: Name of the database object (table, column, etc.).
        type: Type of the database object.
        success: Whether the operation was successful.
    """

    name: str
    type: str
    success: bool


class DatabaseService:
    """Database service for managing SQLAlchemy connections and operations.

    Provides database connection management, session handling, table validation,
    and database initialization functionality.
    """

    name = "database_service"

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
        print("database init success")

    def _create_engine(self) -> "Engine":
        """Create the engine for the database."""
        connect_args = {}
        return create_engine(
            self.database_url,
            connect_args=connect_args,
            echo=False,
            pool_size=self.pool_size,
            max_overflow=self.max_overflow,
            pool_recycle=self.pool_recycle,
        )

    def __enter__(self):
        """Enter the context manager and create a database session.

        Returns:
            Session: The database session.
        """
        self._session = Session(self.engine)
        return self._session

    def __exit__(self, exc_type, exc_value, traceback):
        """Exit the context manager and handle session cleanup.

        Args:
            exc_type: Exception type if an exception occurred.
            exc_value: Exception value if an exception occurred.
            traceback: Exception traceback if an exception occurred.
        """
        if exc_type is not None:  # If an exception has been raised
            print(
                f"Session rollback because of exception: "
                f"{exc_type.__name__} {exc_value}"
            )
            self._session.rollback()
        else:
            self._session.commit()
        self._session.close()

    def get_session(self):
        """Get a database session.

        Yields:
            Session: SQLAlchemy session object.
        """
        with Session(self.engine) as session:
            yield session

    def check_table(self, model):
        """Check if a table and its columns exist in the database.

        Args:
            model: SQLModel class to check against database.

        Returns:
            list[Result]: List of Result objects for table and column checks.
        """
        results = []
        inspector = inspect(self.engine)
        table_name = model.__tablename__
        expected_columns = list(model.__fields__.keys())
        try:
            available_columns = [
                col["name"] for col in inspector.get_columns(table_name)
            ]
            results.append(Result(name=table_name, type="table", success=True))
        except sa.exc.NoSuchTableError:
            logger.error(f"Missing table: {table_name}")
            results.append(Result(name=table_name, type="table", success=False))
            return results

        for column in expected_columns:
            if column not in available_columns:
                logger.error(f"Missing column: {column} in table {table_name}")
                results.append(Result(name=column, type="column", success=False))
            else:
                results.append(Result(name=column, type="column", success=True))
        return results

    def create_db_and_tables(self):
        """Create database tables based on SQLModel metadata.

        Raises:
            RuntimeError: If table creation fails or required tables don't exist.
        """
        inspector = inspect(self.engine)
        table_names = inspector.get_table_names()
        current_tables = ["tools_schema"]

        if table_names and all(table in table_names for table in current_tables):
            logger.debug("Database and tables already exist")
            return

        logger.debug("Creating database and tables")

        for table in SQLModel.metadata.sorted_tables:
            try:
                table.create(self.engine, checkfirst=True)
            except OperationalError as oe:
                logger.warning(
                    f"Table {table} already exists, skipping. Exception: {oe}"
                )
            except Exception as exc:
                logger.error(f"Error creating table {table}: {exc}")
                raise RuntimeError(f"Error creating table {table}") from exc

        # Now check if the required tables exist, if not, something went wrong.
        inspector = inspect(self.engine)
        table_names = inspector.get_table_names()
        for table in current_tables:
            if table not in table_names:
                logger.error("Something went wrong creating the database and tables.")
                logger.error("Please check your database settings.")
                raise RuntimeError(
                    "Something went wrong creating the database and tables."
                )

        logger.debug("Database and tables created successfully")


class RedisService:
    """Redis service for caching operations.

    Provides Redis connection management, caching operations, and both
    single Redis and Redis Cluster support.
    """

    name = "redis_service"

    def __init__(self, cluster_addr=None, password=None, expiration_time=60 * 60):
        self._client = self.init_redis_cluster(cluster_addr, password)
        print("redis init success")
        self.expiration_time = expiration_time

    def init_redis_cluster(self, cluster_addr, password):
        """
        初始化 redis 集群连接
        :param cluster_addr: 格式如下 addr1:port1,addr2:port2,addr3:port3
        :param password:
        :return:
        """
        logger.debug("redis cluster init in progress")
        if os.getenv(const.REDIS_CLUSTER_ADDR_KEY):
            host_port_pairs = cluster_addr.split(",")
            cluster_nodes = []
            for pair in host_port_pairs:
                match = re.match(r"([^:]+):(\d+)", pair)
                if match:
                    host = match.group(1)
                    port = match.group(2)
                    cluster_nodes.append({"host": host, "port": port})
            return RedisCluster(startup_nodes=cluster_nodes, password=password)

        match = re.match(r"([^:]+):(\d+)", cluster_addr)
        if match:
            host = match.group(1)
            port = match.group(2)
            return redis.Redis(host=host, port=port, password=password)

    # check connection
    def is_connected(self):
        """
        Check if the Redis client is connected.
        """
        try:
            self._client.ping()
            return True
        except redis.exceptions.ConnectionError:
            return False

    def get(self, key):
        """
        Retrieve an item from the cache.

        Args:
            key: The key of the item to retrieve.

        Returns:
            The value associated with the key, or None if the key is not found.
        """
        value = self._client.get(key)
        return json.loads(value.decode("utf-8")) if value else None

    def hash_get(self, name, key):
        """Get a field value from a Redis hash.

        Args:
            name: The hash name.
            key: The field key within the hash.

        Returns:
            Any: The deserialized value from the hash field.

        Raises:
            TypeError: If the value cannot be deserialized.
        """
        try:
            result = self._client.hget(name=name, key=key)
            print("result: ", result)
            result_str = result.decode("utf-8")
            return json.loads(result_str)
        except TypeError as exc:
            raise TypeError(
                "RedisCache only accepts values that can be pickled. "
            ) from exc

    def hash_del(self, name, *key):
        """Delete one or more fields from a Redis hash.

        Args:
            name: The hash name.
            *key: Variable number of field keys to delete.

        Returns:
            tuple: (success_boolean, dict_of_failed_deletions)

        Raises:
            TypeError: If the operation fails due to type issues.
        """
        try:
            result = self._client.hdel(name, *key)
            need_delete = {}
            if result != len(key):
                if self._client.exists(result):
                    for field in key:
                        if self._client.hexists(name, field):
                            need_delete.update({name: field})
                            logger.error(f"failed to delete key {name} field {field}")
                        else:
                            logger.info(f"key {name} field {field} has been delete")
                else:
                    logger.info(f"key {name} has been delete")
            return result == len(key), need_delete
        except TypeError as exc:
            raise TypeError(
                "RedisCache only accepts values that can be pickled. "
            ) from exc

    def hash_get_all(self, name):
        """Get all field-value pairs from a Redis hash.

        Args:
            name: The hash name.

        Returns:
            dict: Dictionary with all field-value pairs from the hash.

        Raises:
            TypeError: If values cannot be deserialized.
        """
        try:
            return_dict: Dict = {}
            result: Dict = self._client.hgetall(name=name)
            if result:
                for key in result.keys():
                    key_str = key
                    if isinstance(key, bytes):
                        key_str = key.decode("utf-8")
                    return_dict.update(
                        {key_str: json.loads(result[key].decode("utf-8"))}
                    )
                    result[key] = json.loads(result[key].decode("utf-8"))
            # print(f"succeed to get return_dict {return_dict}")
            return return_dict
        except TypeError as exc:
            raise TypeError(
                "RedisCache only accepts values that can be pickled. "
            ) from exc

    def upsert(self, key, value):
        """
        Inserts or updates a value in the cache.
        If the existing value and the new value are both dictionaries, they are merged.

        Args:
            key: The key of the item.
            value: The value to insert or update.
        """
        existing_value = self.get(key)
        if (
            existing_value is not None
            and isinstance(existing_value, dict)
            and isinstance(value, dict)
        ):
            existing_value.update(value)
            value = existing_value

        self.set(key, value)

    def set(self, key, value):
        """
        Store an item in the cache.

        Args:
            key: The key of the item to store.
            value: The value to store.
        """
        serialized_value = json.dumps(value)
        self._client.set(key, serialized_value, ex=self.expiration_time)

    def delete(self, key):
        """
        Remove an item from the cache.

        Args:
            key: The key of the item to remove.
        """
        self._client.delete(key)

    def clear(self):
        """
        Clear all items from the cache.
        """
        self._client.flushdb()

    def __contains__(self, key):
        """Check if the key is in the cache."""
        return False if key is None else self._client.exists(key)

    def __getitem__(self, key):
        """Retrieve an item from the cache using the square bracket notation."""
        return self.get(key)

    def __setitem__(self, key, value):
        """Add an item to the cache using the square bracket notation."""
        self.set(key, value)

    def __delitem__(self, key):
        """Remove an item from the cache using the square bracket notation."""
        self.delete(key)

    def __repr__(self):
        """Return a string representation of the RedisCache instance."""
        return f"RedisCache(expiration_time={self.expiration_time})"
