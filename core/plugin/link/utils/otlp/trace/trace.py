"""OTLP distributed tracing initialization and configuration.

Configures OpenTelemetry tracing infrastructure including OTLP span exporters,
tracer providers, and batch processors. Provides initialization functions for
setting up distributed tracing to OTLP-compatible backends.
"""

from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import SERVICE_NAME, Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from plugin.link.utils.sid.sid_generator2 import get_host_ip


def init_trace(
    endpoint: str,
    service_name: str,
    timeout: int = 5000,
    max_queue_size: int = 2048,
    schedule_delay_millis: int = 5000,
    max_export_batch_size: int = 512,
    export_timeout_millis: int = 30000,
):
    """
    Initialize trace
    :param endpoint:        OTLP endpoint
    :param service_name:    Service name
    :param timeout:         Timeout duration
    :param max_queue_size:          Maximum queue size for BatchSpanProcessor
                                    data export. Default: 2048
    :param schedule_delay_millis:   Delay interval between two consecutive exports in
                                    BatchSpanProcessor. Default: 5000
    :param max_export_batch_size:   Maximum batch size for BatchSpanProcessor
                                    data export. Default: 512
    :param export_timeout_millis:   Maximum allowed time for data export from
                                    BatchSpanProcessor. Default: 30000
    :return:
    """
    assert endpoint is not None, "otlp endpoint is None"
    assert service_name is not None, "service_name is None"

    resource = Resource(
        attributes={
            SERVICE_NAME: service_name,
            "ip": get_host_ip(),
            "serviceName": service_name,
        }
    )

    exporter = OTLPSpanExporter(insecure=True, endpoint=endpoint, timeout=timeout)

    processor = BatchSpanProcessor(
        exporter,
        max_queue_size=max_queue_size,
        schedule_delay_millis=schedule_delay_millis,
        max_export_batch_size=max_export_batch_size,
        export_timeout_millis=export_timeout_millis,
    )

    provider = TracerProvider(resource=resource)
    provider.add_span_processor(processor)

    # Set global default tracer provider
    trace.set_tracer_provider(provider)
    print("trace init success")
