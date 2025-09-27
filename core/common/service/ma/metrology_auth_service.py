import os
from typing import Optional

from common.metrology_auth import MASDK
from common.service.base import Service, ServiceType


class MASDKService(Service):
    name = ServiceType.MASDK_SERVICE

    def __init__(
        self,
        channel_list: list[str],
        strategy_type: list[str],
        polaris_url: str = "",
        polaris_project: str = "",
        polaris_group: str = "",
        polaris_service: str = "",
        polaris_version: str = "",
        rpc_config_file: Optional[str] = None,
        metrics_service_name: Optional[str] = None,
    ):
        if not os.getenv("MASDK_SWITCH"):
            return
        self.ma_sdk = MASDK(
            channel_list,
            strategy_type,
            polaris_url,
            polaris_project,
            polaris_group,
            polaris_service,
            polaris_version,
            rpc_config_file,
            metrics_service_name,
        )
