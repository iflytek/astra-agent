import abc
from enum import Enum
from typing import Any, Dict

from workflow.extensions.middleware.utils import ServiceType


class RedisModel(Enum):
    """
    Represents the type of Redis model.
    """

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
    def hash_set_ex(
        self, name: str, key: str, value: Any, expire_time: int | None
    ) -> None:
        """
        Add a hash item to the cache with optional expiration.

        :param name: The hash key name.
        :param key: The field key within the hash.
        :param value: The value to cache.
        :param expire_time: Expiration time in seconds for the hash key.
        """

    @abc.abstractmethod
    def hash_get(self, name: str, key: str) -> Any:
        """
        Retrieve a hash field value from the cache.

        :param name: The hash key name.
        :param key: The field key within the hash.
        :return: The value associated with the field, or None if not found.
        """

    @abc.abstractmethod
    def hash_del(self, name: str, key: str) -> Any:
        """
        Delete a hash field from the cache.

        :param name: The hash key name.
        :param key: The field key to delete.
        :return: The result of the deletion operation.
        """

    @abc.abstractmethod
    def hash_get_all(self, name: str) -> Dict[str, Any]:
        """
        Retrieve all fields and values from a hash.

        :param name: The hash key name.
        :return: A dictionary containing all field-value pairs in the hash.
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
        Create a Redis pipeline for batch operations.

        :return: A Redis pipeline object for executing multiple commands atomically.
        """

    @abc.abstractmethod
    def blpop(self, key: str, timeout: int) -> Any:
        """
        Blocking left pop operation on a list.

        :param key: The list key to pop from.
        :param timeout: Maximum time to wait for an element in seconds.
        :return: The popped element or None if timeout.
        """

    @abc.abstractmethod
    def hgetall_str(self, name: str) -> Dict[str, str]:
        """
        Retrieve all fields and values from a hash as strings.

        :param name: The hash key name.
        :return: A dictionary containing all field-value pairs as strings.
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
