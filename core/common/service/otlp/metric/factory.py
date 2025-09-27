import os

from common.otlp.args import global_otlp_metric_args
from common.otlp.metrics.metric import init_metric
from common.service.base import ServiceFactory, ServiceType
from common.service.otlp.metric.metric_service import OtlpMetricService


def init_otlp_metric() -> None:

    # global global_otlp_metric_args

    if global_otlp_metric_args.inited:
        return

    global_otlp_metric_args.otlp_endpoint = os.getenv("OTLP_ENDPOINT", "")
    global_otlp_metric_args.otlp_service_name = os.getenv("SERVICE_NAME", "")
    global_otlp_metric_args.otlp_dc = os.getenv("SERVICE_LOCATION", "")
    global_otlp_metric_args.metric_timeout = int(
        os.getenv("OTLP_METRIC_TIMEOUT", "5000")
    )
    global_otlp_metric_args.metric_export_interval_millis = int(
        os.getenv("OTLP_METRIC_EXPORT_INTERVAL_MILLIS", "3000")
    )
    global_otlp_metric_args.metric_export_timeout_millis = int(
        os.getenv("OTLP_METRIC_EXPORT_TIMEOUT_MILLIS", "5000")
    )
    init_metric(
        global_otlp_metric_args.otlp_endpoint,
        global_otlp_metric_args.otlp_service_name,
        global_otlp_metric_args.metric_timeout,
        global_otlp_metric_args.metric_export_interval_millis,
        global_otlp_metric_args.metric_export_timeout_millis,
    )


class OtlpMetricFactory(ServiceFactory):
    name = ServiceType.OSS_SERVICE

    def __init__(self) -> None:
        super().__init__(OtlpMetricService)  # type: ignore[arg-type]

    def create(self, *args: tuple, **kwargs: dict) -> OtlpMetricService:
        init_otlp_metric()
        # if config.mode == "public":
        # return # api service
        return OtlpMetricService()  # type: ignore[return-value]
