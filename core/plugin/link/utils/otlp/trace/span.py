"""OTLP distributed tracing span management.

Provides span creation and management for OpenTelemetry distributed tracing.
Allows creating trace spans, adding attributes, recording events, and handling
exceptions for comprehensive request tracing and monitoring.
"""

import inspect
import os
import time
from contextlib import contextmanager
from typing import Iterator

from opentelemetry import trace
from opentelemetry.trace import Status
from opentelemetry.util import types
from plugin.link.consts import const


class Span:
    """OpenTelemetry span for distributed tracing.

    Manages trace spans for distributed request tracing. Provides functionality
    to create spans, add attributes, record events, and capture exceptions.
    Integrates with OpenTelemetry tracing infrastructure for observability.

    Attributes:
        sid: Session identifier for trace correlation
        app_id: Application identifier
        uid: User identifier for request context
    """

    sid: str
    app_id: str
    uid: str

    def __init__(self, app_id: str = "", uid="", sid=""):
        self.app_id = app_id
        self.uid = uid
        self.sid = sid
        self.tracer = trace.get_tracer(os.getenv(const.OTLP_SERVICE_NAME_KEY))

    @contextmanager
    def start(
        self,
        func_name: str = "",
        add_source_function_name: bool = False,
        attributes: dict = None,
    ) -> Iterator["Span"]:
        """
        开始一个span
        :param func_name:   方法名
        :param add_source_function_name: 是否添加原始调用方法名
        :param attributes:  属性
        :return:
        """
        if not func_name:
            func_name = self._get_source_function_name()
        if func_name and add_source_function_name:
            func_name = func_name + "::" + self._get_source_function_name()
        default_attr = {
            "sid": self.sid,
            "app_id": self.app_id,
            "uid": self.uid,
            "span_version": "1.0.0",
        }
        if attributes:
            default_attr.update(attributes)

        with self.tracer.start_as_current_span(func_name, attributes=default_attr):
            yield self

    def _get_source_function_name(self):
        frame = inspect.currentframe().f_back.f_back
        return frame.f_back.f_code.co_name

    def set_attribute(self, key: str, value):
        """
        设置属性
        :param key:
        :param value:
        :return:
        """
        self.get_otlp_span().set_attribute(key, value)

    def set_status(self, status: Status):
        """
        设置状态
        :param status:
        :return:
        """
        self.get_otlp_span().set_status(status)

    def set_attributes(self, attributes: dict):
        """
        设置属性
        :param attributes:
        :return:
        """
        self.get_otlp_span().set_attributes(attributes)

    def get_otlp_span(self):
        """Get the current OpenTelemetry span.

        Returns the active OTLP span from the current tracing context.
        Used for direct span operations and attribute management.

        Returns:
            The current OpenTelemetry span instance
        """
        return trace.get_current_span()

    def record_exception(self, ex: Exception, attributes: types.Attributes = None):
        """
        记录异常
        :param attributes:
        :param ex:
        :return:
        """
        self.get_otlp_span().record_exception(
            ex, attributes=attributes, timestamp=int(int(round(time.time() * 1000)))
        )

    def add_event(
        self, name: str, attributes: types.Attributes = None, timestamp: int = None
    ):
        """
        添加事件，如日志
        :param name:
        :param attributes:
        :param timestamp:
        :return:
        """
        self.get_otlp_span().add_event(name, attributes=attributes, timestamp=timestamp)

    def add_info_event(self, value: str):
        """
        添加INFO事件
        :param value:
        :return:
        """
        self.get_otlp_span().add_event("INFO", attributes={"INFO LOG": value})

    def add_info_events(
        self, attributes: types.Attributes = None, timestamp: int = None
    ):
        """
        添加INFO事件
        :param attributes:
        :param timestamp:
        :return:
        """
        self.get_otlp_span().add_event(
            "INFO", attributes=attributes, timestamp=timestamp
        )

    def add_error_event(self, value):
        """

        :param attributes:
        :param timestamp:
        :return:
        """
        self.get_otlp_span().add_event("ERROR", attributes={"ERROR LOG": value})

    def add_error_events(
        self, attributes: types.Attributes = None, timestamp: int = None
    ):
        """

        :param attributes:
        :param timestamp:
        :return:
        """
        self.get_otlp_span().add_event(
            "ERROR", attributes=attributes, timestamp=timestamp
        )
