"""
OSS Service Manager module.

This module provides concrete implementations of OSS services,
including S3-compatible storage and iFly Gateway Storage clients.
"""

import os

from workflow.extensions.middleware.base import Service
from workflow.extensions.middleware.otlp.base import BaseOTLPService
from workflow.extensions.otlp.metric.metric import init_metric
from workflow.extensions.otlp.sid.sid_generator2 import init_sid
from workflow.extensions.otlp.trace.trace import init_trace
from workflow.extensions.otlp.util.ip import ip


class OtlpService(BaseOTLPService, Service):
    """
    OTLP service implementation.
    """

    def __init__(self) -> None:
        # Initialize metrics collection with OTLP configuration
        init_metric(
            endpoint=os.getenv("OTLP_ENDPOINT") or "",
            service_name=os.getenv("SERVICE_NAME") or "",
            timeout=int(os.getenv("OTLP_METRIC_TIMEOUT", "5000")),
            export_interval_millis=int(
                os.getenv("OTLP_METRIC_EXPORT_INTERVAL_MILLIS", "3000")
            ),
            export_timeout_millis=int(
                os.getenv("OTLP_METRIC_EXPORT_TIMEOUT_MILLIS", "5000")
            ),
        )

        # Initialize service identification generator
        init_sid(
            sub=os.getenv("SERVICE_SUB", "spf"),
            location=os.getenv("SERVICE_LOCATION", "SparkFlow"),
            localIp=ip,
            localPort=os.getenv("SERVICE_PORT", "7860"),
        )

        # Initialize distributed tracing with OTLP configuration
        init_trace(
            endpoint=os.getenv("OTLP_ENDPOINT") or "",
            service_name=os.getenv("SERVICE_NAME") or "",
            timeout=int(os.getenv("OTLP_TRACE_TIMEOUT", "5000")),
            max_queue_size=int(os.getenv("OTLP_TRACE_MAX_QUEUE_SIZE", "2048")),
            schedule_delay_millis=int(
                os.getenv("OTLP_TRACE_SCHEDULE_DELAY_MILLIS", "5000")
            ),
            max_export_batch_size=int(
                os.getenv("OTLP_TRACE_MAX_EXPORT_BATCH_SIZE", "512")
            ),
            export_timeout_millis=int(
                os.getenv("OTLP_TRACE_EXPORT_TIMEOUT_MILLIS", "30000")
            ),
        )
