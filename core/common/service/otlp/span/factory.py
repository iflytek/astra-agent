import os

from common.otlp.args import global_otlp_trace_args
from common.otlp.trace.trace import init_trace
from common.service.base import ServiceFactory, ServiceType
from common.service.otlp.span.span_service import OtlpSpanService


def init_otlp_span() -> None:

    # global global_otlp_trace_args

    if global_otlp_trace_args.inited:
        return

    global_otlp_trace_args.otlp_endpoint = os.getenv("OTLP_ENDPOINT", "")
    global_otlp_trace_args.otlp_service_name = os.getenv("SERVICE_NAME", "")
    global_otlp_trace_args.otlp_dc = os.getenv("SERVICE_LOCATION", "")

    global_otlp_trace_args.trace_timeout = int(os.getenv("OTLP_TRACE_TIMEOUT", "3000"))
    global_otlp_trace_args.trace_max_queue_size = int(
        os.getenv("OTLP_TRACE_MAX_QUEUE_SIZE", "2048")
    )
    global_otlp_trace_args.trace_schedule_delay_millis = int(
        os.getenv("OTLP_TRACE_SCHEDULE_DELAY_MILLIS", "3000")
    )
    global_otlp_trace_args.trace_max_export_batch_size = int(
        os.getenv("OTLP_TRACE_MAX_EXPORT_BATCH_SIZE", "512")
    )
    global_otlp_trace_args.trace_export_timeout_millis = int(
        os.getenv("OTLP_TRACE_EXPORT_TIMEOUT_MILLIS", "3000")
    )
    init_trace(
        endpoint=global_otlp_trace_args.otlp_endpoint,
        service_name=global_otlp_trace_args.otlp_service_name,
        timeout=global_otlp_trace_args.trace_timeout,
        max_queue_size=global_otlp_trace_args.trace_max_queue_size,
        schedule_delay_millis=global_otlp_trace_args.trace_schedule_delay_millis,
        max_export_batch_size=global_otlp_trace_args.trace_max_export_batch_size,
        export_timeout_millis=global_otlp_trace_args.trace_export_timeout_millis,
    )


class OtlpSpanFactory(ServiceFactory):
    name = ServiceType.OTLP_SPAN_SERVICE

    def __init__(self) -> None:
        super().__init__(OtlpSpanService)  # type: ignore[arg-type]

    def create(self, *args: tuple, **kwargs: dict) -> OtlpSpanService:
        init_otlp_span()  # type: ignore[report-unused-awaitable]
        return OtlpSpanService()  # type: ignore[return-value]
