"""
Service getter functions for accessing middleware services.

This module provides convenient getter functions for accessing various
middleware services through the service manager. These functions provide
type-safe access to services and handle the casting to appropriate types.
"""

from typing import Iterator, cast

from sqlmodel import Session  # type: ignore
from workflow.extensions.middleware.cache.base import BaseCacheService
from workflow.extensions.middleware.database.manager import DatabaseService
from workflow.extensions.middleware.kafka.manager import KafkaProducerService
from workflow.extensions.middleware.manager import service_manager
from workflow.extensions.middleware.masdk.manager import MASDKService
from workflow.extensions.middleware.oss.base import BaseOSSService
from workflow.extensions.middleware.utils import ServiceType


def get_db_service() -> "DatabaseService":
    """
    Get the database service instance.

    :return: The database service instance
    """
    return cast(DatabaseService, service_manager.get(ServiceType.DATABASE_SERVICE))


def get_session() -> Iterator["Session"]:
    """
    Get a database session from the database service.

    This function provides a generator that yields database sessions,
    which can be used for database operations.

    :return: An iterator of database sessions
    """
    db_service = cast(
        DatabaseService, service_manager.get(ServiceType.DATABASE_SERVICE)
    )
    yield from db_service.get_session()


def get_cache_service() -> "BaseCacheService":
    """
    Get the cache service instance.

    :return: The cache service instance
    """
    return cast(BaseCacheService, service_manager.get(ServiceType.CACHE_SERVICE))


def get_kafka_producer_service() -> "KafkaProducerService":
    """
    Get the Kafka producer service instance.

    :return: The Kafka producer service instance
    """
    return cast(
        KafkaProducerService, service_manager.get(ServiceType.KAFKA_PRODUCER_SERVICE)
    )


def get_oss_service() -> "BaseOSSService":
    """
    Get the OSS (Object Storage Service) instance.

    :return: The OSS service instance
    """
    return cast(BaseOSSService, service_manager.get(ServiceType.OSS_SERVICE))


def get_masdk_service() -> "MASDKService":
    """
    Get the MASDK service instance.

    :return: The MASDK service instance
    """
    return cast(MASDKService, service_manager.get(ServiceType.MASDK_SERVICE))
