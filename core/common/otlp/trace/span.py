import inspect
import json
import os
import time
import uuid
from contextlib import contextmanager
from typing import TYPE_CHECKING, Any, Dict, Iterator, Optional

from opentelemetry import trace
from opentelemetry.trace import Status, StatusCode
from opentelemetry.util import types

if TYPE_CHECKING:
    from common.service.oss.base_oss import BaseOSSService

# from common.otlp.sid import sid_generator2 as sid_gen
from common.otlp import sid as sid_module
from common.otlp.log_trace.node_log import NodeLog
from common.otlp.trace.trace import SpanLevel

# from xf_langflow.otlp.log_trace.node_log import NodeLog

SPAN_SIZE_LIMIT = 10 * 1024


class Span:
    sid: str
    app_id: str
    uid: str
    chat_id: str

    def __init__(
        self,
        app_id: str = "",
        uid: str = "",  # type: ignore[report-untyped-call]
        chat_id: str = "",  # type: ignore[report-untyped-call]
        oss_service: Optional["BaseOSSService"] = None,
    ) -> None:
        self.app_id = app_id
        self.uid = uid
        self.chat_id = chat_id
        if sid_module.sid_generator2 is None:
            raise Exception("sid_generator2 is not initialized")
        self.sid = sid_module.sid_generator2.gen()
        self.tracer = trace.get_tracer(os.getenv("SERVICE_NAME", "service_trace"))
        self.oss_service = oss_service

    @contextmanager
    def start(
        self,
        func_name: str = "",
        add_source_function_name: bool = False,
        attributes: Optional[dict] = None,  # type: ignore[report-untyped-call]
        trace_context: Optional[Dict] = None,  # type: ignore[report-untyped-call]
    ) -> Iterator["Span"]:
        """
        开始一个span
        :param func_name:                   方法名
        :param add_source_function_name:    是否添加原始调用方法名
        :param attributes:                  属性
        :param trace_context:               trace上下文
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
            "chat_id": self.chat_id,
            "span_version": "1.0.0",
        }
        if attributes:
            default_attr.update(attributes)

        context = None
        if trace_context:
            from common.otlp.trace.trace import Trace as CTrace

            context = CTrace.extract_context(trace_context)

        with self.tracer.start_as_current_span(
            func_name, context=context, attributes=default_attr  # type: ignore[arg-type]
        ):
            yield self

    def _get_source_function_name(self) -> str:
        cf = inspect.currentframe()
        if cf:
            frame = cf.f_back
            if frame:
                frame = frame.f_back
                if frame:
                    frame = frame.f_back
                    if frame:
                        f_code = frame.f_code
                        if f_code:
                            return f_code.co_name
        return "unknown"

    def set_attribute(
        self, key: str, value: Any, node_log: Optional[NodeLog] = None  # type: ignore[report-untyped-call]
    ) -> None:
        """
        设置属性
        :param node_log:
        :param key:
        :param value:
        :return:
        """
        self.get_otlp_span().set_attribute(key, value)
        if node_log:
            node_log.add_info_log(f"set attribute: {key}={value}")

    def set_status(self, status: Status) -> None:
        """
        设置状态
        :param status:
        :return:
        """
        self.get_otlp_span().set_status(status)

    def set_attributes(
        self, attributes: dict, node_log: Optional[NodeLog] = None  # type: ignore[report-untyped-call]
    ) -> None:
        """
        设置属性
        :param node_log:
        :param attributes:
        :return:
        """
        self.get_otlp_span().set_attributes(attributes)
        if node_log:
            node_log.add_info_log(f"set attributes: {attributes}")

    def set_code(self, code: int, node_log: Optional[NodeLog] = None) -> None:
        """
        设置状态码
        :param node_log:
        :param code:
        :return:
        """
        self.set_attribute("code", code, node_log)

    def get_otlp_span(self) -> trace.Span:
        return trace.get_current_span()

    def record_exception(
        self,
        ex: Exception,
        attributes: Optional[types.Attributes] = None,  # type: ignore[report-untyped-call]
        node_log: Optional[NodeLog] = None,
    ) -> None:
        """
        记录异常
        :param node_log:
        :param attributes:
        :param ex:
        :return:
        """
        self.get_otlp_span().record_exception(
            ex, attributes=attributes, timestamp=int(int(round(time.time() * 1000)))
        )
        self.set_status(Status(StatusCode.ERROR))
        if node_log:
            node_log.add_error_log(f"{str(ex)}")
            node_log.set_end()
            node_log.running_status = False

    def add_event(
        self,
        name: str,
        attributes: Optional[types.Attributes] = None,  # type: ignore[report-untyped-call]
        timestamp: Optional[int] = None,
        node_log: Optional[NodeLog] = None,
    ) -> None:
        """
        添加事件，如日志
        :param node_log:
        :param name:
        :param attributes:
        :param timestamp:
        :return:
        """
        self.get_otlp_span().add_event(name, attributes=attributes, timestamp=timestamp)
        if node_log and attributes:
            node_log.add_info_log(f"{name}={attributes}")

    def add_info_event(self, value: str, node_log: Optional[NodeLog] = None) -> None:
        """
        添加INFO事件
        :param node_log: 节点链路日志
        :param value:
        :return:
        """
        value_bytes = value.encode("utf-8")
        if len(value_bytes) >= SPAN_SIZE_LIMIT:
            try:
                if self.oss_service is not None:
                    trace_link = self.oss_service.upload_file(
                        f"{str(uuid.uuid4())}", value_bytes
                    )
                    value = f"trace_link: {trace_link}"
            except Exception:
                value = "日志内容过大，上传s3存储时失败"
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
        添加INFO事件
        :param node_log:
        :param attributes:
        :param timestamp:
        :return:
        """
        value_bytes = json.dumps(attributes, ensure_ascii=False).encode("utf-8")
        if len(value_bytes) >= SPAN_SIZE_LIMIT:
            try:
                if self.oss_service is not None:
                    trace_link = self.oss_service.upload_file(
                        f"{str(uuid.uuid4())}", value_bytes
                    )
                    attributes = {"trace_link": trace_link}
            except Exception:
                attributes = {"error": "日志内容过大，上传s3存储时失败"}
        self.get_otlp_span().add_event(
            SpanLevel.INFO.value, attributes=attributes, timestamp=timestamp
        )
        if node_log:
            node_log.add_info_log(f"{attributes}")

    def add_error_event(self, value: str, node_log: Optional[NodeLog] = None) -> None:
        """

        :param value:
        :param node_log:
        :return:
        """
        self.set_attribute("error", True)
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

        :param node_log:
        :param attributes:
        :param timestamp:
        :return:
        """
        self.get_otlp_span().add_event(
            SpanLevel.ERROR.value, attributes=attributes, timestamp=timestamp
        )
        if node_log:
            node_log.add_error_log(f"{attributes}")
            node_log.set_end()
            node_log.running_status = False
