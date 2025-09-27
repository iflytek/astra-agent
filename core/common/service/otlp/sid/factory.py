import os

from common.otlp.args import global_otlp_sid_args
from common.otlp.sid import SidInfo, init_sid
from common.service.base import ServiceFactory, ServiceType
from common.service.otlp.sid.sid_service import OtlpSidService


def init_otlp_sid() -> None:

    # global global_otlp_sid_args

    if global_otlp_sid_args.inited:
        return

    global_otlp_sid_args.otlp_endpoint = os.getenv("OTLP_ENDPOINT", "")
    global_otlp_sid_args.otlp_service_name = os.getenv("SERVICE_NAME", "")
    global_otlp_sid_args.otlp_dc = os.getenv("SERVICE_LOCATION", "")

    global_otlp_sid_args.sid_sub = os.getenv("SERVICE_SUB", "svc")
    global_otlp_sid_args.sid_location = os.getenv("SERVICE_LOCATION", "src")
    global_otlp_sid_args.sid_local_port = os.getenv("SERVICE_PORT")  # type: ignore[assignment]
    init_sid(
        SidInfo(
            sub=global_otlp_sid_args.sid_sub,
            location=global_otlp_sid_args.sid_location,
            local_ip=global_otlp_sid_args.sid_ip,
            local_port=global_otlp_sid_args.sid_local_port,
            index=0,
        )
    )


class OtlpSidFactory(ServiceFactory):
    name = ServiceType.OTLP_SID_SERVICE

    def __init__(self) -> None:
        super().__init__(OtlpSidService)  # type: ignore[arg-type]

    def create(self) -> OtlpSidService:  # type: ignore[override, no-untyped-def]
        init_otlp_sid()
        return OtlpSidService()
