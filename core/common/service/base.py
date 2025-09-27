"""
base.py
"""

from abc import ABC
from enum import Enum
from typing import List


class Service(ABC):
    """
    Base class for all services.
    """

    name: "ServiceType"
    ready: bool = False

    def teardown(self) -> None:
        """
        Teardown the service.
        """

    def set_ready(self) -> None:
        """
        Set the service as ready.
        """
        self.ready = True


class ServiceFactory:
    """
    Base class for service factories.

    """

    def __init__(self, service_class: Service) -> None:
        self.service_class = service_class

    def create(self, *args: tuple, **kwargs: dict) -> Service:  # type: ignore[report-unknown-return-type]
        """
        Create a service instance.
        """
        raise NotImplementedError

    def get_service_class(self) -> Service:
        """获取服务类"""
        return self.service_class


class ServiceType(str, Enum):
    """
    Enum for the different types of services that can be
    registered with the service manager.
    """

    CACHE_SERVICE = "cache_service"
    DATABASE_SERVICE = "database_service"
    LOG_SERVICE = "log_service"
    KAFKA_PRODUCER_SERVICE = "kafka_producer_service"
    OSS_SERVICE = "oss_service"
    MASDK_SERVICE = "masdk_service"
    OTLP_METRIC_SERVICE = "otlp_metric_service"
    OTLP_NODE_LOG_SERVICE = "otlp_node_log_service"
    OTLP_SPAN_SERVICE = "otlp_span_service"
    OTLP_SID_SERVICE = "otlp_sid_service"
    SETTINGS_SERVICE = "settings_service"

    @staticmethod
    def list() -> List[str]:
        """list of service types"""
        return list(map(lambda c: c.value, ServiceType))
