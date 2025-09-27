import abc
from enum import Enum
from typing import Any

from common.service.base import ServiceType


class RedisModel(Enum):
    SINGLE = 1
    CLUSTER = 2


class BaseCacheService(abc.ABC):
    """
    Abstract base class for a cache.
    """

    name = ServiceType.CACHE_SERVICE

    @abc.abstractmethod
    def get(self, key: str) -> Any:
        """
        Retrieve an item from the cache.

        Args:
            key: The key of the item to retrieve.

        Returns:
            The value associated with the key, or None if the key is not found.
        """

    @abc.abstractmethod
    def set(self, key: str, value: Any) -> None:
        """
        Add an item to the cache.

        Args:
            key: The key of the item.
            value: The value to cache.
        """

    @abc.abstractmethod
    def hash_set_ex(self, name: str, key: str, value: Any, expire_time: int) -> None:
        """
        add a hash item to the cache

        Args:
            name: the key of the item.
            key: The field of the item.
            value: The value to cache.
            expire_time: key的超时时间
        """

    @abc.abstractmethod
    def hash_get(self, name: str, key: str) -> Any:
        """
        add a hash item to the cache

        Args:
            name: the key of the item.
            key: The field of the item.
        """

    @abc.abstractmethod
    def hash_del(self, name: str, key: str) -> None:
        """
        description: 删除hash field
        """

    @abc.abstractmethod
    def hash_get_all(self, name: str) -> Any:
        """
        add a hash item to the cache

        Args:
            name: the key of the item.
        """

    @abc.abstractmethod
    def upsert(self, key: str, value: Any) -> None:
        """
        Add an item to the cache if it doesn't exist, or update it if it does.

        Args:
            key: The key of the item.
            value: The value to cache.
        """

    @abc.abstractmethod
    def delete(self, key: str) -> None:
        """
        Remove an item from the cache.

        Args:
            key: The key of the item to remove.
        """

    @abc.abstractmethod
    def clear(self) -> None:
        """
        Clear all items from the cache.
        """

    @abc.abstractmethod
    def pipeline(self) -> Any:
        """
        return redis pipe
        """

    @abc.abstractmethod
    def blpop(self, key: str, timeout: int) -> Any:
        """
        return redis blpop method
        """

    @abc.abstractmethod
    def hgetall_str(self, name: str) -> Any:
        """
        return redis str_data
        """

    @abc.abstractmethod
    def __contains__(self, key: str) -> bool:
        """
        Check if the key is in the cache.

        Args:
            key: The key of the item to check.

        Returns:
            True if the key is in the cache, False otherwise.
        """

    @abc.abstractmethod
    def __getitem__(self, key: str) -> Any:
        """
        Retrieve an item from the cache using the square bracket notation.

        Args:
            key: The key of the item to retrieve.
        """

    @abc.abstractmethod
    def __setitem__(self, key: str, value: Any) -> None:
        """
        Add an item to the cache using the square bracket notation.

        Args:
            key: The key of the item.
            value: The value to cache.
        """

    @abc.abstractmethod
    def __delitem__(self, key: str) -> None:
        """
        Remove an item from the cache using the square bracket notation.

        Args:
            key: The key of the item to remove.
        """

    def is_connected(self) -> bool:
        return True
