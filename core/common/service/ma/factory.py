import os

from common.service.base import ServiceFactory
from common.service.ma.metrology_auth_service import MASDKService


class MASDKServiceFactory(ServiceFactory):
    def __init__(self) -> None:
        super().__init__(MASDKService)  # type: ignore[arg-type]

    def create(self):  # type: ignore[override, no-untyped-def]
        polaris_url = os.getenv("MASDK_POLARIS_URL")
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL 环境变量未配置")

        polaris_project = os.getenv("MASDK_POLARIS_PROJECT")
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL 环境变量未配置")

        polaris_group = os.getenv("MASDK_POLARIS_GROUP")
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL 环境变量未配置")

        polaris_service = os.getenv("MASDK_POLARIS_SERVICE")
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL 环境变量未配置")

        polaris_version = os.getenv("MASDK_POLARIS_VERSION")
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL 环境变量未配置")

        channel_list = [os.getenv("MASDK_CHANNEL")]
        if not polaris_url:
            raise ValueError("MASDK_POLARIS_URL 环境变量未配置")

        metrics_service_name = "masdk"
        strategy_type = ["cnt", "conc"]

        return MASDKService(
            channel_list,  # type: ignore[arg-type]
            strategy_type,  # type: ignore[arg-type]
            polaris_url,  # type: ignore[arg-type]
            polaris_project,  # type: ignore[arg-type]
            polaris_group,  # type: ignore[arg-type]
            polaris_service,  # type: ignore[arg-type]
            polaris_version,  # type: ignore[arg-type]
            None,  # type: ignore[arg-type]
            metrics_service_name,  # type: ignore[arg-type]
        )
