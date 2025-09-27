import os

from loguru import logger

from common.service.base import ServiceFactory, ServiceType
from common.service.oss.base_oss import BaseOSSService
from common.service.oss.ifly_storage_gateway_service import IFlyGatewayStorageClient


class OSSServiceFactory(ServiceFactory):
    name = ServiceType.OSS_SERVICE  # type: ignore[report-untyped-call]

    def __init__(self) -> None:  # type: ignore[report-untyped-call]
        super().__init__(BaseOSSService)  # type: ignore[arg-type]
        self.client = None

    def create(self):  # type: ignore[override, no-untyped-def]
        logger.debug("Creating OSS Servie")
        oss_type = os.getenv("OSS_TYPE", "ifly_gateway_storage")
        if oss_type == "s3":
            pass
        else:
            self.client = IFlyGatewayStorageClient(  # type: ignore[assignment]
                endpoint=os.getenv("OSS_ENDPOINT", ""),
                access_key_id=os.getenv("OSS_ACCESS_KEY_ID", ""),
                access_key_secret=os.getenv("OSS_ACCESS_KEY_SECRET", ""),
                bucket_name=os.getenv("OSS_BUCKET_NAME", ""),
                ttl=int(os.getenv("OSS_TTL", "86400")),
            )
        return self.client
