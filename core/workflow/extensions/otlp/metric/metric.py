import os

from loguru import logger
from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.metrics import get_meter_provider, set_meter_provider
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import SERVICE_NAME, Resource

from workflow.extensions.otlp.metric.consts import (
    SERVER_REQUEST_DESC,
    SERVER_REQUEST_TIME_DESC,
    SERVER_REQUEST_TIME_MICROSECONDS,
    SERVER_REQUEST_TOTAL,
)

# SDK metric reporting interval, recommended less than 30000ms, default 1000ms
# export_interval_millis = 3000
# Default configuration for metrics reporting server timeout in ms, default 5000ms
# export_timeout_millis = 5000
# Default configuration for server connection timeout in ms, default 5000ms
# timeout = 1000
# OpenTelemetry endpoint address
# endpoint = "172.30.209.27:4317"

# Global metric objects
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
    Initialize the OpenTelemetry metrics system.

    This function sets up the metric collection and export infrastructure,
    including the OTLP exporter, metric reader, and meter provider.

    :param endpoint: OpenTelemetry collector endpoint address
    :param service_name: Name of the service for metric identification
    :param timeout: Server connection timeout in milliseconds, default 5000ms
    :param export_interval_millis: SDK metric reporting interval in milliseconds, recommended less than 30000ms, default 1000ms
    :param export_timeout_millis: Metrics reporting server timeout in milliseconds, default 5000ms
    """

    global counter, histogram, meter

    if os.getenv("OTLP_ENABLE", "1") == "1":
        assert endpoint is not None, "endpoint is None"
        assert service_name is not None, "service_name is None"
        # Create OTLP metric exporter with insecure connection
        exporter = OTLPMetricExporter(
            insecure=True,
            endpoint=endpoint,
            timeout=timeout,
            max_export_batch_size=1000,
        )

        # Create periodic metric reader for automatic export
        metric_reader = PeriodicExportingMetricReader(
            exporter,
            export_interval_millis=export_interval_millis,
            export_timeout_millis=export_timeout_millis,
        )
        metric_readers = [metric_reader]
    else:
        logger.info("Metrics reporting is disabled by environment variable")
        metric_readers = []

    # Create resource with service name attribute
    resource = Resource(attributes={SERVICE_NAME: service_name})
    provider = MeterProvider(metric_readers=metric_readers, resource=resource)

    # Set global default MeterProvider
    set_meter_provider(provider)

    # Create a Meter from the global MeterProvider
    meter = get_meter_provider().get_meter(f"{service_name}_meter")

    # Create counter metric for request counts
    counter = meter.create_counter(
        SERVER_REQUEST_TOTAL, description=SERVER_REQUEST_DESC
    )
    # Create histogram metric for request timing
    histogram = meter.create_histogram(
        SERVER_REQUEST_TIME_MICROSECONDS, description=SERVER_REQUEST_TIME_DESC
    )
    logger.debug("metric init success")
