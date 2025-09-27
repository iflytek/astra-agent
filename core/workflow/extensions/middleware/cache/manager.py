import pickle
import re
from typing import Any, Dict, Tuple

from loguru import logger
from workflow.extensions.middleware.base import Service
from workflow.extensions.middleware.cache.base import BaseCacheService, RedisModel


class RedisCache(BaseCacheService, Service):
    """
    Redis cluster-based cache implementation.

    Provides a Redis cluster cache service with support for basic operations,
    hash operations, and pipeline operations.
    """

    def __init__(
        self,
        addr: str,
        password: str,
        expiration_time: int = 60 * 60,
        model: RedisModel = RedisModel.CLUSTER,
    ) -> None:
        """
        Initialize Redis cache with cluster configuration.

        :param addr: Redis addresses in format "host1:port1,host2:port2" or "host:port"
        :param password: Redis authentication password
        :param expiration_time: Default expiration time in seconds (default: 3600)
        :param model: Redis model type (default: RedisModel.CLUSTER)
        """
        if model == RedisModel.CLUSTER:
            self._client = self.init_redis_cluster(addr, password)
        else:
            self._client = self.init_redis(addr, password)
        logger.debug("redis init success")
        self.expiration_time = expiration_time

    def init_redis_cluster(self, cluster_addr: str, password: str) -> Any:
        """
        Initialize Redis cluster connection.

        :param cluster_addr: Cluster addresses in format "addr1:port1,addr2:port2,addr3:port3"
        :param password: Redis authentication password
        :return: RedisCluster client instance
        """
        logger.debug("redis cluster init in progress")
        from rediscluster import RedisCluster  # type: ignore

        host_port_pairs = cluster_addr.split(",")
        cluster_nodes = []
        for pair in host_port_pairs:
            match = re.match(r"([^:]+):(\d+)", pair)
            if match:
                host = match.group(1)
                port = match.group(2)
                cluster_nodes.append({"host": host, "port": port})
        return RedisCluster(startup_nodes=cluster_nodes, password=password)

    def init_redis(self, addr: str, password: str) -> Any:
        """
        Initialize Redis connection.

        :param addr: Redis addresses in format "host:port"
        :param password: Redis authentication password
        :return: Redis client instance
        """
        logger.debug("redis init in progress")
        from redis import Redis  # type: ignore

        host, port = addr.split(":")
        return Redis(host=host, port=port, password=password)

    def is_connected(self) -> bool:
        """
        Check if the Redis client is connected.

        :return: True if connected, False otherwise
        """
        import redis  # type: ignore

        try:
            self._client.ping()
            return True
        except redis.exceptions.ConnectionError:
            return False

    def get(self, key: str) -> Any:
        """
        Retrieve an item from the cache.

        Args:
            key: The key of the item to retrieve.

        Returns:
            The value associated with the key, or None if the key is not found.
        """
        value = self._client.get(key)
        return pickle.loads(value) if value else None

    def set(self, key: str, value: Any) -> None:
        """
        Add an item to the cache.

        Args:
            key: The key of the item.
            value: The value to cache.
        """
        try:
            if pickled := pickle.dumps(value):
                result = self._client.setex(key, self.expiration_time, pickled)
                if not result:
                    raise ValueError("RedisCache could not set the value.")
        except TypeError as exc:
            raise TypeError(
                "RedisCache only accepts values that can be pickled. "
            ) from exc

    def hash_set_ex(
        self, name: str, key: str, value: Any, expire_time: int | None
    ) -> None:
        """
        Set a hash field with optional expiration.

        :param name: The hash key name
        :param key: The field key within the hash
        :param value: The value to cache
        :param expire_time: Expiration time in seconds for the hash key
        :raises TypeError: If the value cannot be pickled
        """
        try:
            if pickled := pickle.dumps(value):
                result = self._client.hset(name=name, key=key, value=pickled)
                if result != 1:
                    if self._client.hexists(name=name, key=key):
                        logger.error(
                            f"update hash key {name} field {key} value {value}"
                        )
                    else:
                        logger.error(f"hash set failed, ret {result}")
                    if self._client.exists(name) and expire_time:
                        self._client.expire(name=name, time=expire_time)
                    return
                if expire_time:
                    self._client.expire(name=name, time=expire_time)
        except TypeError as exc:
            raise TypeError(
                "RedisCache only accepts values that can be pickled. "
            ) from exc

    def hash_get(self, name: str, key: str) -> Any:
        """
        Get a hash field value.

        :param name: The hash key name
        :param key: The field key within the hash
        :return: The unpickled value or None if not found
        :raises TypeError: If the value cannot be unpickled
        """
        try:
            result = self._client.hget(name=name, key=key)
            if result:
                return pickle.loads(result)
            else:
                return result
        except TypeError as exc:
            raise TypeError(
                "RedisCache only accepts values that can be pickled. "
            ) from exc

    def hash_del(self, name: str, *key: str) -> Tuple[bool, Dict[str, str]]:
        """
        Delete hash fields.

        :param name: The hash key name
        :param key: Variable number of field keys to delete
        :return: Tuple of (success_flag, failed_deletions_dict)
        :raises TypeError: If there's an error during the operation
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

    def hash_get_all(self, name: str) -> Dict[str, Any]:
        """
        Get all fields and values from a hash using HSCAN.

        Uses HSCAN to efficiently retrieve all hash fields in batches,
        handling large hashes without blocking Redis.

        :param name: The hash key name
        :return: Dictionary containing all field-value pairs
        :raises TypeError: If any value cannot be unpickled
        """
        result = {}
        cursor = 0
        while True:
            cursor, data = self._client.hscan(name, cursor=cursor, count=100)
            for key, value in data.items():
                key_str = key.decode("utf-8") if isinstance(key, bytes) else key
                try:
                    if isinstance(value, bytes):
                        result[key_str] = pickle.loads(value)
                    else:
                        result[key_str] = value
                except (pickle.PickleError, ValueError, EOFError) as exc:
                    raise TypeError(
                        f"RedisCache only accepts values that can be pickled. "
                        f"Failed to unpickle field '{key_str}'"
                    ) from exc
            if cursor == 0:
                break
        return result

    def upsert(self, key: str, value: Any) -> None:
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

    def delete(self, key: str) -> None:
        """
        Remove an item from the cache.

        Args:
            key: The key of the item to remove.
        """
        self._client.delete(key)

    def clear(self) -> None:
        """
        Clear all items from the cache.
        """
        self._client.flushdb()

    def pipeline(self) -> Any:
        """
        Create a Redis pipeline for batch operations.

        :return: Redis pipeline object for executing multiple commands atomically
        """
        return self._client.pipeline()

    def blpop(self, key: str, timeout: int) -> Any:
        """
        Blocking left pop operation on a list.

        :param key: The list key to pop from
        :param timeout: Maximum time to wait for an element in seconds
        :return: The popped element or None if timeout
        """
        return self._client.blpop(key, timeout=timeout)

    def hgetall_str(self, name: str) -> Dict[str, str]:
        """
        Get all hash fields and values as strings.

        :param name: The hash key name
        :return: Dictionary with string keys and values
        """
        result = self._client.hgetall(name)
        return {k.decode(): v.decode() for k, v in result.items()} if result else {}

    def __contains__(self, key: str) -> bool:
        """
        Check if the key exists in the cache.

        :param key: The key to check
        :return: True if key exists, False otherwise
        """
        return False if key is None else self._client.exists(key)

    def __getitem__(self, key: str) -> Any:
        """
        Retrieve an item from the cache using square bracket notation.

        :param key: The key of the item to retrieve
        :return: The value associated with the key
        """
        return self.get(key)

    def __setitem__(self, key: str, value: Any) -> None:
        """
        Add an item to the cache using square bracket notation.

        :param key: The key of the item
        :param value: The value to cache
        """
        self.set(key, value)

    def __delitem__(self, key: str) -> None:
        """
        Remove an item from the cache using square bracket notation.

        :param key: The key of the item to remove
        """
        self.delete(key)

    def __repr__(self) -> str:
        """
        Return a string representation of the RedisCache instance.

        :return: String representation showing expiration time
        """
        return f"RedisCache(expiration_time={self.expiration_time})"
