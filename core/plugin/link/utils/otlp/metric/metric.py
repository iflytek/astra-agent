"""OTLP metric initialization and configuration.

Configures OpenTelemetry metric collection including OTLP exporters, meter providers,
and metric instruments (counters and histograms). Provides initialization functions
for setting up telemetry data collection to OTLP-compatible backends.
"""

from opentelemetry.exporter.otlp.proto.grpc.metric_exporter import OTLPMetricExporter
from opentelemetry.metrics import get_meter_provider, set_meter_provider
from opentelemetry.sdk.metrics import MeterProvider
from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
from opentelemetry.sdk.resources import SERVICE_NAME, Resource
from plugin.link.utils.otlp.metric.consts import (
    SERVER_REQUEST_DESC,
    SERVER_REQUEST_TIME_DESC,
    SERVER_REQUEST_TIME_MICROSECONDS,
    SERVER_REQUEST_TOTAL,
)

# SDK metric reporting interval, recommended < 30000ms, default 1000ms
# export_interval_millis = 3000
# Default configuration for metric reporting server timeout in ms, default 5000ms
# export_timeout_millis = 5000
# Default configuration for server connection time in ms, default 5000ms
# timeout = 1000
# OpenTelemetry address
# endpoint = "172.30.209.27:4317"

COUNTER = None
HISTOGRAM = None
METER = None


def init_metric(
    endpoint: str,
    service_name: str,
    timeout: int = 5000,
    export_interval_millis: int = 1000,
    export_timeout_millis: int = 5000,
):
    """
    Initialize metric
    :param endpoint:                OpenTelemetry address
    :param service_name:            Service name
    :param timeout:                 Default server connection time in ms, default 5000ms
    :param export_interval_millis:  SDK metric reporting interval,
                                    recommended < 30000ms, default 1000ms
    :param export_timeout_millis:   Default metric reporting server timeout in ms,
                                    default 5000ms
    :return:
    """

    global COUNTER, HISTOGRAM, METER

    assert endpoint is not None, "endpoint is None"
    assert service_name is not None, "service_name is None"

    exporter = OTLPMetricExporter(
        insecure=True, endpoint=endpoint, timeout=timeout, max_export_batch_size=1000
    )

    metric_reader = PeriodicExportingMetricReader(
        exporter,
        export_interval_millis=export_interval_millis,
        export_timeout_millis=export_timeout_millis,
    )

    resource = Resource(attributes={SERVICE_NAME: service_name})
    provider = MeterProvider(metric_readers=[metric_reader], resource=resource)

    # Set global default MeterProvider
    set_meter_provider(provider)

    # Create a Meter from global MeterProvider
    METER = get_meter_provider().get_meter(f"{service_name}_meter")

    COUNTER = METER.create_counter(
        SERVER_REQUEST_TOTAL, description=SERVER_REQUEST_DESC
    )
    HISTOGRAM = METER.create_histogram(
        SERVER_REQUEST_TIME_MICROSECONDS, description=SERVER_REQUEST_TIME_DESC
    )
    print("metric init success")
