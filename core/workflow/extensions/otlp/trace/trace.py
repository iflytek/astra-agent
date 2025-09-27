import json
import os
from enum import Enum
from typing import Any, Sequence

from loguru import logger
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import SERVICE_NAME, Resource
from opentelemetry.sdk.trace import ReadableSpan, SpanLimits, TracerProvider
from opentelemetry.sdk.trace.export import (
    BatchSpanProcessor,
    SpanExporter,
    SpanExportResult,
)
from opentelemetry.trace import StatusCode
from workflow.extensions.otlp.util.ip import ip


class SpanLevel(Enum):
    """
    Enumeration of span log levels for OpenTelemetry tracing.
    """

    DEBUG = "DEBUG"
    INFO = "INFO"
    WARN = "WARN"
    ERROR = "ERROR"


class FileSpanExporter(SpanExporter):
    """
    Custom span exporter that writes trace information to local files.

    This exporter processes spans and logs them using different log levels
    based on the span name and status code.
    """

    def export(self, spans: Sequence[ReadableSpan]) -> SpanExportResult:
        """
        Export spans to local files using appropriate log levels.

        :param spans: Sequence of readable spans to export
        :return: Export result indicating success or failure
        """
        try:
            for span in spans:
                # Remove newlines from span's native to_json method
                content = f"Span: {json.dumps(json.loads(span.to_json()), ensure_ascii=False)}"

                # Log based on span name and status code
                if (
                    span.name == SpanLevel.ERROR.value
                    or span.status.status_code == StatusCode.ERROR
                ):
                    logger.error(content)
                elif span.name == SpanLevel.INFO.value:
                    logger.info(content)
                elif span.name == SpanLevel.WARN.value:
                    logger.warning(content)
                else:
                    logger.debug(content)
        except Exception as e:
            logger.error(f"Error exporting spans: {e}")
        return SpanExportResult.SUCCESS

    def shutdown(self) -> None:
        """
        Shutdown the exporter.

        This is a no-op implementation as no cleanup is required.
        """
        return None


def init_trace(
    endpoint: str,
    service_name: str,
    timeout: int = 5000,
    max_queue_size: int = 2048,
    schedule_delay_millis: int = 5000,
    max_export_batch_size: int = 512,
    export_timeout_millis: int = 30000,
    span_limit: int = 1000,
) -> None:
    """
    Initialize OpenTelemetry tracing with OTLP exporter and file exporter.

    :param endpoint: OTLP endpoint URL for trace export
    :param service_name: Name of the service being traced
    :param timeout: Timeout for OTLP export operations in milliseconds
    :param max_queue_size: Maximum queue size for BatchSpanProcessor data export (default: 2048)
    :param schedule_delay_millis: Delay interval between consecutive exports in BatchSpanProcessor (default: 5000)
    :param max_export_batch_size: Maximum batch size for BatchSpanProcessor data export (default: 512)
    :param export_timeout_millis: Maximum allowed time for data export from BatchSpanProcessor (default: 30000)
    :param span_limit: Maximum number of spans that can be tracked per tracer (default: 1000)
    """
    # Validate required parameters
    assert endpoint is not None, "otlp endpoint is None"
    assert service_name is not None, "service_name is None"

    # Configure span limits
    span_limits = SpanLimits(max_events=span_limit)

    # Create resource with service information
    resource = Resource(
        attributes={
            SERVICE_NAME: service_name,
            "ip": ip,
            "serviceName": service_name,
        }
    )

    # Create tracer provider and add OTLP processor
    provider = TracerProvider(resource=resource, span_limits=span_limits)

    # Create OTLP exporter for remote trace export
    if os.getenv("OTLP_ENABLE", "0") == "1":
        exporter = OTLPSpanExporter(insecure=True, endpoint=endpoint, timeout=timeout)
        processor = BatchSpanProcessor(
            exporter,
            max_queue_size=max_queue_size,
            schedule_delay_millis=schedule_delay_millis,
            max_export_batch_size=max_export_batch_size,
            export_timeout_millis=export_timeout_millis,
        )
        provider.add_span_processor(processor)

    # Add file exporter for local persistence
    file_exporter = FileSpanExporter()
    file_processor = BatchSpanProcessor(file_exporter)
    provider.add_span_processor(file_processor)

    # Set global default tracer provider
    trace.set_tracer_provider(provider)
    logger.debug("trace init success")


class Trace:
    """
    Utility class for trace context management in distributed tracing.

    Provides static methods for injecting and extracting trace context
    to enable distributed tracing across service boundaries.
    """

    @staticmethod
    def inject_context() -> dict:
        """
        Extract trace context from the current active span.

        Gets the trace context from the global context for the currently active span.

        :return: Dictionary containing trace context information
        """
        from opentelemetry.propagate import inject

        trace_context: dict[str, Any] = {}
        inject(trace_context)
        return trace_context

    @staticmethod
    def extract_context(trace_context: Any) -> Any:
        """
        Extract trace context from a carrier and use it to continue tracing.

        :param trace_context: Trace context dictionary to extract from
        :return: Extracted trace context for continuing the trace
        """
        from opentelemetry.propagate import extract

        return extract(trace_context)
