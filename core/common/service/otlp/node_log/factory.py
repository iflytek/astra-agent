from common.service.base import ServiceFactory, ServiceType
from common.service.otlp.node_log.node_log_service import OtlpNodeLogService


class OtlpNodeLogFactory(ServiceFactory):
    name = ServiceType.OTLP_NODE_LOG_SERVICE

    def __init__(self) -> None:
        super().__init__(OtlpNodeLogService)  # type: ignore[arg-type]

    def create(self) -> OtlpNodeLogService:  # type: ignore[override, no-untyped-def]
        return OtlpNodeLogService()
