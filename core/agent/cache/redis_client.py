from abc import ABC, abstractmethod
from typing import Any, Optional

import redis
from pydantic import BaseModel, ConfigDict, Field
from rediscluster import ClusterConnectionPool, RedisCluster

from exceptions.middleware_exc import PingRedisExc


class RedisClientCache(BaseModel):
    model_config = ConfigDict(arbitrary_types_allowed=True)

    client: Any = Field(default=None)


class BaseRedisClient(ABC):
    """Redis客户端抽象基类"""

    @abstractmethod
    async def get(self, name: str) -> bytes | None:
        pass

    @abstractmethod
    async def set(
        self,
        name: str,
        value: str,
        *,
        ex: int | None = None,
        px: int | None = None,
        nx: bool = False,
        xx: bool = False,
    ) -> bool:
        pass

    @abstractmethod
    async def delete(self, name: str) -> int:
        pass

    @abstractmethod
    async def get_ttl(self, name: str) -> Optional[int]:
        pass


class RedisStandaloneClient(BaseModel, BaseRedisClient):
    """Redis单机客户端"""

    model_config = ConfigDict(arbitrary_types_allowed=True)

    host: str
    port: int
    password: str
    _client: Optional[redis.Redis] = None

    async def create_client(self) -> redis.Redis:
        """创建单机客户端"""
        if self._client is None:
            self._client = redis.Redis(
                host=self.host,
                port=self.port,
                password=self.password,
                decode_responses=False,
            )

        await self.is_connected(self._client)
        return self._client

    async def get(self, name: str) -> bytes | None:
        client = await self.create_client()
        result = client.get(name)
        return result if isinstance(result, bytes) else None

    async def set(
        self,
        name: str,
        value: str,
        *,
        ex: int | None = None,
        px: int | None = None,
        nx: bool = False,
        xx: bool = False,
    ) -> bool:
        client = await self.create_client()
        result = client.set(name, value, ex=ex, px=px, nx=nx, xx=xx)
        return bool(result)

    async def delete(self, name: str) -> int:
        client = await self.create_client()
        result = client.delete(name)
        return int(result)

    async def get_ttl(self, name: str) -> int:
        """获取指定 key 的剩余过期时间（秒）"""
        client = await self.create_client()
        result = client.ttl(name)
        return int(result)

    @staticmethod
    async def is_connected(client: redis.Redis) -> bool:
        """检查Redis单机客户端连接"""
        try:
            client.ping()
            return True
        except Exception as e:
            raise PingRedisExc from e


class RedisClusterClient(BaseModel, BaseRedisClient):
    model_config = ConfigDict(arbitrary_types_allowed=True)

    nodes: str
    password: str
    _client: Optional[RedisCluster] = None

    async def create_client(self) -> RedisCluster:
        """创建客户端"""
        if self._client is None:
            nodes = []
            for node in self.nodes.split(","):
                node_parts = node.strip().split(":")
                if len(node_parts) != 2:
                    raise ValueError(
                        f"Invalid Redis node format: '{node}'. Expected format: 'host:port'"
                    )
                node_addr, node_port = node_parts
                nodes.append({"host": node_addr, "port": int(node_port)})

            pool = ClusterConnectionPool(startup_nodes=nodes, password=self.password)
            self._client = RedisCluster(connection_pool=pool)

        await self.is_connected(self._client)

        return self._client

    async def get(self, name: str) -> bytes | None:

        client = await self.create_client()
        result = client.get(name)
        return result if isinstance(result, bytes) else None

    async def set(
        self,
        name: str,
        value: str,
        *,
        ex: int | None = None,
        px: int | None = None,
        nx: bool = False,
        xx: bool = False,
    ) -> bool:
        client = await self.create_client()
        result = client.set(name, value, ex=ex, px=px, nx=nx, xx=xx)
        return bool(result)

    async def delete(self, name: str) -> int:
        client = await self.create_client()
        result = client.delete(name)
        return int(result)

    async def get_ttl(self, name: str) -> Optional[int]:
        """获取指定 key 的剩余过期时间（秒）
        :param name: 要查询的 key
        :return: 剩余过期时间（秒），若 key 不存在返回 -2，若 key 存在但无过期时间返回 -1
        """
        client = await self.create_client()
        result = client.ttl(name)
        # Handle the complex return type from redis ttl command
        if isinstance(result, int):
            return result
        return None

    @staticmethod
    async def is_connected(client: RedisCluster) -> bool:
        """
        Check if the Redis client is connected.
        """

        try:
            client.ping()
            return True
        except Exception as e:
            raise PingRedisExc from e


def create_redis_client(
    cluster_addr: str = "", standalone_addr: str = "", password: str = ""
) -> BaseRedisClient:
    """Redis客户端工厂方法

    Args:
        cluster_addr: 集群地址，格式为 'host1:port1,host2:port2'
        standalone_addr: 单机地址，格式为 'host:port'
        password: Redis密码

    Returns:
        BaseRedisClient: Redis客户端实例

    优先级：cluster_addr > standalone_addr
    """
    if cluster_addr:
        return RedisClusterClient(nodes=cluster_addr, password=password)
    elif standalone_addr:
        addr_parts = standalone_addr.strip().split(":")
        if len(addr_parts) != 2:
            raise ValueError(
                f"Invalid Redis standalone address format: '{standalone_addr}'. Expected format: 'host:port'"
            )
        host, port = addr_parts
        return RedisStandaloneClient(host=host, port=int(port), password=password)
    else:
        raise ValueError("Either cluster_addr or standalone_addr must be provided")
