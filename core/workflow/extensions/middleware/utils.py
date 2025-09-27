"""
Utility functions and types for middleware services.

This module provides service type definitions and utility functions
for managing service factories and their dependencies.
"""

from enum import Enum
from typing import Any, List, Tuple


class ServiceType(str, Enum):
    """
    Enumeration of available middleware service types.

    This enum defines all the different types of services that can be
    registered with the service manager. Each service type corresponds
    to a specific middleware component.
    """

    CACHE_SERVICE = "cache_service"
    DATABASE_SERVICE = "database_service"
    LOG_SERVICE = "log_service"
    KAFKA_PRODUCER_SERVICE = "kafka_producer_service"
    OSS_SERVICE = "oss_service"
    MASDK_SERVICE = "masdk_service"
    OTLP_SERVICE = "otlp_service"


def get_factories_and_deps() -> List[Tuple[Any, List[ServiceType]]]:
    """
    Get all service factories and their dependencies.

    This function returns a list of tuples containing service factories
    and their corresponding dependencies. The factories are imported
    dynamically to avoid circular import issues.

    :return: List of tuples containing (factory, dependencies) pairs
    """
    from workflow.extensions.middleware.cache import factory as cache_factory
    from workflow.extensions.middleware.database import factory as database_factory
    from workflow.extensions.middleware.kafka import factory as kafka_producer_factory
    from workflow.extensions.middleware.log import factory as log_factory
    from workflow.extensions.middleware.oss import factory as oss_factory
    from workflow.extensions.middleware.otlp import factory as otlp_factory

    return [
        (
            database_factory.DatabaseServiceFactory(),
            [ServiceType.DATABASE_SERVICE],
        ),
        (
            cache_factory.CacheServiceFactory(),
            [ServiceType.CACHE_SERVICE],
        ),
        (
            kafka_producer_factory.KafkaProducerServiceFactory(),
            [ServiceType.KAFKA_PRODUCER_SERVICE],
        ),
        (
            oss_factory.OSSServiceFactory(),
            [ServiceType.OSS_SERVICE],
        ),
        (log_factory.LogServiceFactory(), [ServiceType.LOG_SERVICE]),
        (otlp_factory.OTLPServiceFactory(), [ServiceType.OTLP_SERVICE]),
    ]
