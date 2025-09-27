import os

from loguru import logger
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.metrics import get_meter_provider, set_meter_provider
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import SERVICE_NAME, Resource

from common.otlp.metrics.consts import (
    SERVER_REQUEST_DESC,
    SERVER_REQUEST_TIME_DESC,
    SERVER_REQUEST_TIME_MICROSECONDS,
    SERVER_REQUEST_TOTAL,
)

counter = None
histogram = None
meter = None


def init_metric(
    endpoint: str,
    service_name: str,
    timeout: int = 5000,
    export_interval_millis: int = 1000,
    export_timeout_millis: int = 5000,
) -> None:
    """
    初始化metric
    :param endpoint:                open telemetry地址
    :param service_name:            服务名称
    :param timeout:                 默认配置 与服务端建连时间 单位ms 默认5000ms
    :param export_interval_millis:  sdk上报metric时间间隔 建议小于30000ms  默认1000ms
    :param export_timeout_millis:   默认配置 metrics上报服务端超时时间 单位ms 默认5000ms
    :return:
    """

    global counter, histogram, meter

    enable_metrics = os.getenv("OTLP_ENABLE", "true").lower() in (
        "true",
        "1",
        "yes",
        "on",
    )

    if enable_metrics:
        exporter = OTLPMetricExporter(
            insecure=True,
            endpoint=endpoint,
            timeout=timeout,
            max_export_batch_size=1000,
        )

        metric_reader = PeriodicExportingMetricReader(
            exporter,
            export_interval_millis=export_interval_millis,
            export_timeout_millis=export_timeout_millis,
        )
        metric_readers = [metric_reader]
    else:
        logger.info("Metrics reporting is disabled by environment variable")
        metric_readers = []

    assert endpoint is not None, "endpoint is None"
    assert service_name is not None, "service_name is None"

    resource = Resource(attributes={SERVICE_NAME: service_name})
    provider = MeterProvider(metric_readers=metric_readers, resource=resource)

    set_meter_provider(provider)

    meter = get_meter_provider().get_meter(f"{service_name}_meter")

    counter = meter.create_counter(
        SERVER_REQUEST_TOTAL, description=SERVER_REQUEST_DESC
    )
    histogram = meter.create_histogram(
        SERVER_REQUEST_TIME_MICROSECONDS, description=SERVER_REQUEST_TIME_DESC
    )
    logger.debug("metric init success")
