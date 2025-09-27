import json
import os
from enum import Enum
from typing import Sequence

from loguru import logger
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.propagate import extract, inject
from opentelemetry.sdk.resources import SERVICE_NAME, Resource
from opentelemetry.sdk.trace import SpanLimits, TracerProvider
from opentelemetry.sdk.trace.export import (
    BatchSpanProcessor,
    SpanExporter,
    SpanExportResult,
)
from opentelemetry.trace import StatusCode

from common.otlp.ip import local_ip


class SpanLevel(Enum):
    DEBUG = "DEBUG"
    INFO = "INFO"
    WARN = "WARN"
    ERROR = "ERROR"


class FileSpanExporter(SpanExporter):

    def export(self, spans: Sequence[trace.Span]) -> SpanExportResult:  # type: ignore[override]
        try:
            for span in spans:
                content = f"Span: {json.dumps(json.loads(span.to_json()), ensure_ascii=False)}"  # type: ignore[attr-defined]

                if (
                    span.name == SpanLevel.ERROR.value  # type: ignore[attr-defined]
                    or span.status.status_code == StatusCode.ERROR  # type: ignore[attr-defined]
                ):
                    logger.error(content)
                elif span.name == SpanLevel.INFO.value:  # type: ignore[attr-defined]
                    logger.info(content)
                elif span.name == SpanLevel.WARN.value:  # type: ignore[attr-defined]
                    logger.warning(content)
                else:
                    logger.debug(content)
        except Exception as e:
            logger.error(f"Error exporting spans: {e}")
        return SpanExportResult.SUCCESS

    def shutdown(self) -> SpanExportResult:  # type: ignore[override]
        return SpanExportResult.SUCCESS


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
    初始化trace
    :param endpoint:        otlp endpoint
    :param service_name:    服务名称
    :param timeout:         超时时间
    :param max_queue_size:          表示BatchSpanProcessor数据导出的最大队列大小。默认值:2048
    :param schedule_delay_millis:   表示BatchSpanProcessor的两个连续导出之间的延迟间隔。默认值:5000
    :param max_export_batch_size:   表示BatchSpanProcessor数据导出的最大批处理大小。默认值:512
    :param export_timeout_millis:   表示从BatchSpanProcessor导出数据的最大允许时间。默认值:30000
    :param span_limit:      表示每个跟踪器可以跟踪的最大span数量。默认值:1000
    :return:
    """
    assert endpoint is not None, "otlp endpoint is None"
    assert service_name is not None, "service_name is None"

    span_limits = SpanLimits(max_events=span_limit)

    resource = Resource(
        attributes={
            SERVICE_NAME: service_name,
            "ip": local_ip,
            "serviceName": service_name,
        }
    )

    provider = TracerProvider(resource=resource, span_limits=span_limits)
    if os.getenv("OTLP_ENABLE", "false").lower() in (
        "true",
        "1",
        "yes",
        "on",
    ):
        exporter = OTLPSpanExporter(insecure=True, endpoint=endpoint, timeout=timeout)

        processor = BatchSpanProcessor(
            exporter,
            max_queue_size=max_queue_size,
            schedule_delay_millis=schedule_delay_millis,
            max_export_batch_size=max_export_batch_size,
            export_timeout_millis=export_timeout_millis,
        )

        provider.add_span_processor(processor)

    file_exporter = FileSpanExporter()
    file_processor = BatchSpanProcessor(file_exporter)
    provider.add_span_processor(file_processor)

    trace.set_tracer_provider(provider)
    logger.debug("trace init success")


class Trace:
    @staticmethod
    def inject_context() -> dict:  # type: ignore[report-untyped-def]
        trace_context: dict = {}
        inject(trace_context)
        return trace_context

    @staticmethod
    def extract_context(trace_context: dict) -> dict:
        return extract(trace_context)
