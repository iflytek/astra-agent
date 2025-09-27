import inspect
import json
import os
import time
import traceback
import uuid
from contextlib import contextmanager
from typing import Any, Dict, Iterator, Optional

from loguru import logger
from opentelemetry import trace
from opentelemetry.trace import Status, StatusCode
from opentelemetry.util import types

import workflow.extensions.otlp.sid.sid_generator2 as sid_gen
from workflow.extensions.middleware.getters import get_oss_service
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.trace import SpanLevel

from .trace import Trace

# Maximum size limit for span content before uploading to OSS
SPAN_SIZE_LIMIT = 10 * 1024


class Span:
    """
    A wrapper class for OpenTelemetry spans that provides additional functionality
    for distributed tracing in the SparkFlow workflow system.

    This class manages span lifecycle, attributes, events, and integrates with
    the node logging system for comprehensive observability.
    """

    sid: str  # Session ID for tracking
    app_id: str  # Application identifier
    uid: str  # User identifier
    chat_id: str  # Chat session identifier

    def __init__(self, app_id: str = "", uid: str = "", chat_id: str = "") -> None:
        """
        Initialize a new Span instance.

        :param app_id: Application identifier
        :param uid: User identifier
        :param chat_id: Chat session identifier
        """
        self.app_id = app_id
        self.uid = uid
        self.chat_id = chat_id

        # Generate session ID if generator is available
        if sid_gen.sid_generator2 is not None:
            self.sid = sid_gen.sid_generator2.gen()
        else:
            self.sid = ""

        # Initialize OpenTelemetry tracer
        self.tracer = trace.get_tracer(os.getenv("OTLP_TRACE_NAME", "spark_flow_trace"))

    @contextmanager
    def start(
        self,
        func_name: str = "",
        add_source_function_name: bool = False,
        attributes: Optional[dict] = None,
        trace_context: Optional[Dict] = None,
    ) -> Iterator["Span"]:
        """
        Start a new span as a context manager.

        :param func_name: Name of the function being traced
        :param add_source_function_name: Whether to append the source function name
        :param attributes: Additional attributes to set on the span
        :param trace_context: Trace context for distributed tracing
        :return: Iterator yielding the current span instance
        """
        # Determine function name for the span
        if not func_name:
            func_name = self._get_source_function_name()
        if func_name and add_source_function_name:
            func_name = func_name + "::" + self._get_source_function_name()

        # Prepare default attributes for the span
        default_attr = {
            "sid": self.sid,
            "app_id": self.app_id,
            "uid": self.uid,
            "chat_id": self.chat_id,
            "span_version": "1.0.0",
        }
        if attributes:
            default_attr.update(attributes)

        # Extract trace context if provided
        context = None
        if trace_context:
            context = Trace.extract_context(trace_context)

        # Start the span and yield control
        with self.tracer.start_as_current_span(
            func_name, context=context, attributes=default_attr
        ):
            yield self

    def _get_source_function_name(self) -> str:
        """
        Get the name of the function that called the span start method.

        :return: Name of the calling function, empty string if not found
        """
        frame = inspect.currentframe()
        if frame is None or frame.f_back is None or frame.f_back.f_back is None:
            return ""
        back2 = frame.f_back.f_back
        if back2.f_back is None or back2.f_code is None:
            return ""
        return back2.f_back.f_code.co_name

    def set_attribute(
        self, key: str, value: Any, node_log: Optional[NodeLog] = None
    ) -> None:
        """
        Set a single attribute on the current span.

        :param key: Attribute key
        :param value: Attribute value
        :param node_log: Optional node log for additional logging
        """
        self.get_otlp_span().set_attribute(key, value)
        if node_log:
            node_log.add_info_log(f"set attribute: {key}={value}")

    def set_status(self, status: Status) -> None:
        """
        Set the status of the current span.

        :param status: OpenTelemetry Status object
        """
        self.get_otlp_span().set_status(status)

    def set_attributes(
        self, attributes: dict, node_log: Optional[NodeLog] = None
    ) -> None:
        """
        Set multiple attributes on the current span.

        :param attributes: Dictionary of attributes to set
        :param node_log: Optional node log for additional logging
        """
        self.get_otlp_span().set_attributes(attributes)
        if node_log:
            node_log.add_info_log(f"set attributes: {attributes}")

    def set_code(self, code: int, node_log: Optional[NodeLog] = None) -> None:
        """
        Set a status code attribute on the current span.

        :param code: Status code to set
        :param node_log: Optional node log for additional logging
        """
        self.set_attribute("code", code, node_log)

    def get_otlp_span(self) -> trace.Span:
        """
        Get the current OpenTelemetry span.

        :return: Current OpenTelemetry span instance
        """
        return trace.get_current_span()

    def record_exception(
        self,
        ex: Exception,
        attributes: Optional[types.Attributes] = None,
        node_log: Optional[NodeLog] = None,
    ) -> None:
        """
        Record an exception on the current span.

        :param ex: Exception to record
        :param attributes: Optional additional attributes
        :param node_log: Optional node log for additional logging
        """
        # Log exception
        logger.opt(depth=1).error(f"sid: {self.sid}, error: {traceback.format_exc()}")
        # Record exception with current timestamp
        self.get_otlp_span().record_exception(
            ex, attributes=attributes, timestamp=int(int(round(time.time() * 1000)))
        )
        # Set span status to error
        self.set_status(Status(StatusCode.ERROR))
        if node_log:
            node_log.add_error_log(f"{str(ex)}")
            node_log.set_end()
            node_log.running_status = False

    def add_event(
        self,
        name: str,
        attributes: Optional[types.Attributes] = None,
        timestamp: Optional[int] = None,
        node_log: Optional[NodeLog] = None,
    ) -> None:
        """
        Add an event to the current span.

        :param name: Event name
        :param attributes: Optional event attributes
        :param timestamp: Optional timestamp for the event
        :param node_log: Optional node log for additional logging
        """
        # Log event
        logger.opt(depth=1).info(f"sid: {self.sid}, event: {name}={attributes}")
        self.get_otlp_span().add_event(name, attributes=attributes, timestamp=timestamp)
        if node_log and attributes:
            node_log.add_info_log(f"{name}={attributes}")

    def add_info_event(self, value: str, node_log: Optional[NodeLog] = None) -> None:
        """
        Add an INFO level event to the current span.

        If the content exceeds the size limit, it will be uploaded to OSS.

        :param value: Information content to log
        :param node_log: Optional node log for additional logging
        """
        # Log event
        logger.opt(depth=1).info(f"sid: {self.sid}, event: {value}")
        # Check if content exceeds size limit
        value_bytes = value.encode("utf-8")
        if len(value_bytes) >= SPAN_SIZE_LIMIT:
            try:
                # Upload large content to OSS and store link
                trace_link = get_oss_service().upload_file(
                    f"{str(uuid.uuid4())}", value_bytes
                )
                value = f"trace_link: {trace_link}"
            except Exception:
                value = "Content too large, failed to upload to OSS storage"

        # Add INFO event to span
        self.get_otlp_span().add_event("INFO", attributes={"INFO LOG": value})
        if node_log:
            node_log.add_info_log(f"{value}")

    def add_info_events(
        self,
        attributes: Optional[types.Attributes] = None,
        timestamp: Optional[int] = None,
        node_log: Optional[NodeLog] = None,
    ) -> None:
        """
        Add multiple INFO level events to the current span.

        If the content exceeds the size limit, it will be uploaded to OSS.

        :param attributes: Event attributes dictionary
        :param timestamp: Optional timestamp for the event
        :param node_log: Optional node log for additional logging
        """
        # Log event
        logger.opt(depth=1).info(f"sid: {self.sid}, event: {attributes}")
        # Check if content exceeds size limit
        value_bytes = json.dumps(attributes, ensure_ascii=False).encode("utf-8")
        if len(value_bytes) >= SPAN_SIZE_LIMIT:
            try:
                # Upload large content to OSS and store link
                trace_link = get_oss_service().upload_file(
                    f"{str(uuid.uuid4())}", value_bytes
                )
                attributes = {"trace_link": trace_link}
            except Exception:
                attributes = {
                    "error": "Content too large, failed to upload to OSS storage"
                }

        # Add INFO event to span
        self.get_otlp_span().add_event(
            SpanLevel.INFO.value, attributes=attributes, timestamp=timestamp
        )
        if node_log:
            node_log.add_info_log(f"{attributes}")

    def add_error_event(self, value: Any, node_log: Optional[NodeLog] = None) -> None:
        """
        Add an ERROR level event to the current span.

        :param value: Error content to log
        :param node_log: Optional node log for additional logging
        """
        # Log event
        logger.opt(depth=1).info(f"sid: {self.sid}, event: {value}")
        # Mark span as having an error
        self.set_attribute("error", True)
        # Add ERROR event to span
        self.get_otlp_span().add_event(
            SpanLevel.ERROR.value, attributes={"ERROR LOG": value}
        )
        if node_log:
            node_log.add_error_log(f"{value}")
            node_log.set_end()
            node_log.running_status = False

    def add_error_events(
        self,
        attributes: Optional[types.Attributes] = None,
        timestamp: Optional[int] = None,
        node_log: Optional[NodeLog] = None,
    ) -> None:
        """
        Add multiple ERROR level events to the current span.

        :param attributes: Error event attributes dictionary
        :param timestamp: Optional timestamp for the event
        :param node_log: Optional node log for additional logging
        """
        # Log event
        logger.opt(depth=1).info(f"sid: {self.sid}, event: {attributes}")
        # Add ERROR event to span
        self.get_otlp_span().add_event(
            SpanLevel.ERROR.value, attributes=attributes, timestamp=timestamp
        )
        if node_log:
            node_log.add_error_log(f"{attributes}")
            node_log.set_end()
            node_log.running_status = False
