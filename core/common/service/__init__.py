"""
__init__.py

"""

from typing import Dict, Generator, List, Optional
from venv import logger

from sqlmodel import Session

from common.service.base import Service, ServiceFactory, ServiceType

# if TYPE_CHECKING:
#     from common.service.cache.base_cache import BaseCacheService
#     from common.service.db.db_service import DatabaseService
#     from common.service.kafka.kafka_service import KafkaProducerService
#     from common.service.ma.metrology_auth_service import MASDKService
#     from common.service.oss.base_oss import BaseOSSService
#     from common.service.otlp.metric.base_metric import BaseOtlpMetricService
#     from common.service.otlp.node_log.base_node_log import BaseOtlpNodeLogService
#     from common.service.otlp.sid.sid_service import OtlpSidService
#     from common.service.otlp.span.span_service import OtlpSpanService
#     from common.service.settings.settings_service import SettingsService


class ServiceManager:
    """
    Service Manager
    """

    def __init__(self) -> None:
        self.services: Dict[str, "Service"] = {}
        self.factories: dict = {}
        self.dependencies: dict = {}

    def register_factory(
        self,
        service_factory: ServiceFactory,
        dependencies: Optional[List[ServiceType]] = None,
    ) -> None:
        """
        Registers a new factory with dependencies.
        """
        if dependencies is None:
            dependencies = []
        service_name = service_factory.service_class.name
        self.factories[service_name] = service_factory
        self.dependencies[service_name] = dependencies
        self._create_service(service_name)

    def get(self, service_name: ServiceType) -> Service:
        """
        Get (or create) a service by its name.
        """
        if service_name not in self.services:
            self._create_service(service_name)

        return self.services[service_name]

    def _create_service(self, service_name: ServiceType) -> None:
        """
        Create a new service given its name, handling dependencies.
        """
        # from loguru import logger
        logger.debug(f"Create service {service_name}")
        self._validate_service_creation(service_name)

        # Create the actual service
        self.services[service_name] = self.factories[service_name].create()
        self.services[service_name].set_ready()

    def _validate_service_creation(self, service_name: ServiceType) -> None:
        """
        Validate whether the service can be created.
        """
        if service_name not in self.factories:
            raise ValueError(
                f"No factory registered for the service class '{service_name.name}'"
            )


service_manager = ServiceManager()


def get_db_service() -> Service:
    return service_manager.get(ServiceType.DATABASE_SERVICE)


def get_session() -> Generator["Session", None, None]:
    db_service = service_manager.get(ServiceType.DATABASE_SERVICE)
    yield from db_service.get_session()  # type: ignore[attr-defined]


def get_cache_service() -> Service:
    return service_manager.get(ServiceType.CACHE_SERVICE)


def get_kafka_producer_service() -> Service:
    """获取kafka生产者服务"""
    return service_manager.get(ServiceType.KAFKA_PRODUCER_SERVICE)


def get_oss_service() -> Service:
    """获取oss服务"""
    return service_manager.get(ServiceType.OSS_SERVICE)


def get_masdk_service() -> Service:
    """获取metrology服务"""
    return service_manager.get(ServiceType.MASDK_SERVICE)


def get_otlp_metric_service() -> Service:
    """获取otlp指标服务"""
    return service_manager.get(ServiceType.OTLP_METRIC_SERVICE)


def get_otlp_span_service() -> Service:
    """获取otlp span服务"""
    return service_manager.get(ServiceType.OTLP_SPAN_SERVICE)


def get_oltp_sid_service() -> Service:
    """获取otlp sid服务"""
    return service_manager.get(ServiceType.OTLP_SID_SERVICE)


def get_otlp_node_log_service() -> Service:
    """获取otlp node log服务"""
    return service_manager.get(ServiceType.OTLP_NODE_LOG_SERVICE)


def get_settings_service() -> Service:
    """获取设置服务"""
    return service_manager.get(ServiceType.SETTINGS_SERVICE)
