import pickle
import re
from typing import Any, Dict, Optional

from loguru import logger
from redis import Redis  # type: ignore[import-untyped, import-not-found]
from rediscluster import RedisCluster  # type: ignore[import-untyped, import-not-found]

from common.service.base import Service, ServiceType
from common.service.cache.base_cache import BaseCacheService, RedisModel


class RedisCache(BaseCacheService, Service):

    name = ServiceType.CACHE_SERVICE

    def __init__(
        self,
        addr: Optional[str] = None,
        password: Optional[str] = None,
        expiration_time: int = 60 * 60,
        model: RedisModel = RedisModel.CLUSTER,
    ):
        if model == RedisModel.CLUSTER:
            self._client = self.init_redis_cluster(addr, password)  # type: ignore[arg-type]
        else:
            self._client = self.init_redis(addr, password)  # type: ignore[arg-type]
        logger.debug("redis init success")
        self.expiration_time = expiration_time

    def init_redis_cluster(self, cluster_addr: str, password: str) -> RedisCluster:
        """
        初始化 redis 集群连接
        :param cluster_addr: 格式如下 addr1:port1,addr2:port2,addr3:port3
        :param password:
        :return:
        """
        logger.debug("redis cluster init in progress")

        host_port_pairs = cluster_addr.split(",")
        cluster_nodes = []
        for pair in host_port_pairs:
            match = re.match(r"([^:]+):(\d+)", pair)
            if match:
                host = match.group(1)
                port = match.group(2)
                cluster_nodes.append({"host": host, "port": port})
        return RedisCluster(startup_nodes=cluster_nodes, password=password)

    def init_redis(self, addr: str, password: str) -> Redis:
        """
        初始化 redis 连接
        :param addr:
        :param password:
        :return:
        """
        logger.debug("redis init in progress")

        host, port = addr.split(":")
        return Redis(host=host, port=int(port), password=password)

    # check connection
    def is_connected(self) -> bool:
        """
        Check if the Redis client is connected.
        """
        import redis

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

    def hash_set_ex(self, name: str, key: str, value: Any, expire_time: int) -> None:
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
                # print(f"succeed to add name {name}, field {key} to redis")
        except TypeError as exc:
            raise TypeError(
                "RedisCache only accepts values that can be pickled. "
            ) from exc

    def hash_get(self, name: str, key: str) -> Any:
        try:
            result = self._client.hget(name=name, key=key)
            # print("result: ", result)
            if result:
                return pickle.loads(result)
            else:
                return result
        except TypeError as exc:
            raise TypeError(
                "RedisCache only accepts values that can be pickled. "
            ) from exc

    def hash_del(self, name: str, *key: str) -> tuple[bool, dict[str, str]]:  # type: ignore[override]
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
        try:
            return_dict: Dict = {}
            result: Dict = self._client.hgetall(name=name)
            if result:
                for key in result.keys():
                    key_str = key
                    if isinstance(key, bytes):
                        key_str = key.decode("utf-8")
                    return_dict.update({key_str: pickle.loads(result[key])})
                    result[key] = pickle.loads(result[key])
            # print(f"succeed to get return_dict {return_dict}")
            return return_dict
        except TypeError as exc:
            raise TypeError(
                "RedisCache only accepts values that can be pickled. "
            ) from exc

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
        return redis pipe
        """
        return self._client.pipeline()

    def blpop(self, key: str, timeout: int) -> Any:
        return self._client.blpop(key, timeout=timeout)

    def hgetall_str(self, name: str) -> Dict[str, str]:
        result = self._client.hgetall(name)
        return {k.decode(): v.decode() for k, v in result.items()} if result else {}

    def __contains__(self, key: str) -> bool:
        """Check if the key is in the cache."""
        return False if key is None else self._client.exists(key)

    def __getitem__(self, key: str) -> Any:
        """Retrieve an item from the cache using the square bracket notation."""
        return self.get(key)

    def __setitem__(self, key: str, value: Any) -> None:
        """Add an item to the cache using the square bracket notation."""
        self.set(key, value)

    def __delitem__(self, key: str) -> None:
        """Remove an item from the cache using the square bracket notation."""
        self.delete(key)

    def __repr__(self) -> str:
        """Return a string representation of the RedisCache instance."""
        return f"RedisCache(expiration_time={self.expiration_time})"
