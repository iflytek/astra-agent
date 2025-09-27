import json
import time
import uuid
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field

from common.otlp.log_trace.base import Usage
from common.otlp.log_trace.node_log import NodeLog
from common.service.oss.base_oss import BaseOSSService


class Status(BaseModel):
    code: int = 0
    message: str = ""


class NodeTraceLog(BaseModel):
    service_id: str
    sid: str
    app_id: str = Field(default="", description="应用ID")
    uid: str = Field(default="", description="用户ID")
    chat_id: str = Field(default="", description="会话ID")
    sub: str
    caller: str = ""
    log_caller: str = ""

    question: str = Field(default="", description="问题")
    answer: str = Field(default="", description="答案")

    start_time: int = Field(default_factory=lambda: int(time.time() * 1000))
    end_time: int = Field(default_factory=lambda: int(time.time() * 1000))
    duration: int = 0
    first_frame_duration: float = -1.0

    srv: Dict[str, str] = {}
    srv_tag: Dict[str, str] = {}
    status: Status = Status()
    usage: Usage = Usage()
    version: str = "v2.0.0"
    trace: List[NodeLog] = Field(default_factory=list)

    class Config:
        arbitrary_types_allowed = True

    def add_q(self, question: str) -> None:
        """
        description: add q
        """
        self.question = question

    def add_a(self, answer: str) -> None:
        """
        description: add a
        """
        self.answer = answer

    def add_first_frame_duration(self, first_frame_duration: int) -> None:
        """
        description: add first frame duration
        """
        self.first_frame_duration = first_frame_duration

    def add_srv(self, key: str, value: str) -> None:
        self.srv[key] = value
        self.srv_tag[key] = value

    def set_end(self) -> None:
        """
        日志结束
        :return:
        """
        self.end_time = int(time.time() * 1000)
        self.duration = self.end_time - self.start_time
        for i, node_log in enumerate(self.trace):
            self.usage.total_tokens += node_log.data.usage.total_tokens
            self.usage.prompt_tokens += node_log.data.usage.prompt_tokens
            self.usage.question_tokens += node_log.data.usage.question_tokens
            self.usage.completion_tokens += node_log.data.usage.completion_tokens

    def set_status(self, code: int, message: str) -> None:
        self.status.code = code
        self.status.message = message

    def add_node_log(self, node_logs: list[NodeLog]) -> None:
        if not node_logs:
            return
        self.trace.extend(node_logs)

    def add_func_log(self, node_logs: list[NodeLog]) -> None:
        self.add_node_log(node_logs)

    def to_json(self, large_field_save_service: Optional[BaseOSSService] = None) -> str:
        """
        返回JSON字符串。超过5kb的value值，存储在对应存储服务中
        :return:
        """

        import sys

        def is_large_string(s: str, limit: int = 5 * 1024) -> bool:
            return isinstance(s, str) and sys.getsizeof(s.encode("utf-8")) > limit

        def process_data(data: Any) -> Any:
            if isinstance(data, dict):
                return {k: process_data(v) for k, v in data.items()}
            elif isinstance(data, list):
                return [process_data(item) for item in data]
            elif isinstance(data, str):
                if is_large_string(data):
                    if isinstance(large_field_save_service, BaseOSSService):
                        return large_field_save_service.upload_file(
                            f"{uuid.uuid4().hex}.txt", data.encode("utf-8")
                        )
                    return data
                else:
                    return data
            else:
                return data

        result = process_data(self.model_dump())

        def json_fallback(obj: Any) -> Any:
            if isinstance(obj, set):
                return list(obj)

        return json.dumps(result, ensure_ascii=False, default=json_fallback)
