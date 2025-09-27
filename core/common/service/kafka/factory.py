import os
from typing import Optional

from common.service.base import ServiceFactory
from common.service.kafka.kafka_service import KafkaProducerService


class KafkaProducerServiceFactory(ServiceFactory):
    def __init__(self) -> None:
        super().__init__(KafkaProducerService)  # type: ignore[arg-type]

    def create(self, servers: Optional[str] = None, **kwargs: dict) -> KafkaProducerService:  # type: ignore[override, no-untyped-def]
        """
        创建 KafkaProducerService 实例
        :param servers: Kafka bootstrap.servers
        :return: KafkaProducerService 实例
        """
        servers = servers or os.getenv("KAFKA_SERVERS")
        if not servers:
            raise ValueError("KAFKA_SERVERS 环境变量未配置")

        config = {"bootstrap.servers": servers, **kwargs}
        return KafkaProducerService(config)
