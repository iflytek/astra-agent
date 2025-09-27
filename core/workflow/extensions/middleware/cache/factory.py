import os

from loguru import logger

from workflow.extensions.middleware.cache.base import BaseCacheService, RedisModel
from workflow.extensions.middleware.cache.manager import RedisCache
from workflow.extensions.middleware.factory import ServiceFactory


class CacheServiceFactory(ServiceFactory):
    """
    Factory class for creating cache service instances.

    This factory creates Redis-based cache services with configuration
    from environment variables.
    """

    def __init__(self) -> None:
        """
        Initialize the cache service factory.

        Sets up the factory to create BaseCacheService instances.
        """
        super().__init__(BaseCacheService)

    def create(self) -> BaseCacheService:
        """
        Create a Redis cache service instance.

        Creates a RedisCache instance with configuration from environment variables:
        - REDIS_EXPIRE: Cache expiration time in seconds (default: 3600)
        - REDIS_CLUSTER_ADDR: Redis cluster addresses in format "host1:port1,host2:port2"
        - REDIS_PASSWORD: Redis authentication password

        :return: A configured RedisCache instance.
        :raises RuntimeError: If unable to connect to Redis cluster.
        """
        logger.debug("Creating Redis cache")
        redis_cluster_addr = os.getenv("REDIS_CLUSTER_ADDR", "")
        redis_addr = os.getenv("REDIS_ADDR", "")
        if not redis_cluster_addr and not redis_addr:
            raise RuntimeError("REDIS_CLUSTER_ADDR or REDIS_ADDR must be set")

        redis_cache = RedisCache(
            expiration_time=int(os.getenv("REDIS_EXPIRE") or "3600"),
            addr=redis_cluster_addr or redis_addr,
            password=os.getenv("REDIS_PASSWORD", ""),
            model=RedisModel.CLUSTER if redis_cluster_addr else RedisModel.SINGLE,
        )

        if redis_cache.is_connected():
            logger.debug("Redis cache is connected")
            return redis_cache
        else:
            raise RuntimeError("Could not connect to Redis cache")
