import json
import time
import uuid
from typing import Any, Dict, Set

from pydantic import BaseModel, Field

from common.otlp.log_trace.base import Usage


class Data(BaseModel):
    input: Dict[str, Any] = {}
    output: Dict[str, Any] = {}
    config: Dict[str, Any] = {}
    usage: Usage = Usage()


class NodeLog(BaseModel):
    id: str = Field(default_factory=lambda: uuid.uuid4().hex)
    sid: str = ""

    node_id: str = ""
    node_type: str = ""
    node_name: str = ""

    func_id: str = ""
    func_type: str = ""
    func_name: str = ""

    next_log_ids: Set[str] = set()

    start_time: int = Field(default_factory=lambda: int(time.time() * 1000))
    end_time: int = Field(default_factory=lambda: int(time.time() * 1000))
    duration: int | tuple = (0,)
    first_frame_duration: int = -1
    node_first_cost_time: float = -1

    llm_output: str = ""
    running_status: bool = True
    data: Data = Data()
    logs: list[str] = []

    def __init__(
        self,
        sid: str,
        func_id: str = "",
        func_name: str = "",
        func_type: str = "",
        **kwargs: Any,
    ):
        node_id = kwargs.get("node_id", "")
        node_type = kwargs.get("node_type", "")
        node_name = kwargs.get("node_name", "")

        func_id = func_id if func_id else node_id
        func_name = func_name if func_name else node_name
        func_type = func_type if func_type else node_type

        super().__init__(
            sid=sid, func_id=func_id, func_name=func_name, func_type=func_type, **kwargs
        )

    def set_next_node_id(self, next_id: str) -> None:
        """
        设置下一个节点ID
        :param next_id:
        :return:
        """
        self.next_log_ids.add(next_id)

    def set_first_frame_duration(self) -> None:
        """
        设置首帧时间
        :return:
        """
        self.first_frame_duration = int(time.time() * 1000) - self.start_time

    def set_node_first_cost_time(self, cost_time: float) -> None:
        self.node_first_cost_time = cost_time

    def set_start(self) -> None:
        self.start_time = int(time.time() * 1000)

    def set_end(self) -> None:
        """
        日志结束
        :return:
        """
        self.end_time = int(time.time() * 1000)
        self.duration = self.end_time - self.start_time

    def append_input_data(self, key: str, data: Any) -> None:
        """
        添加节点输入数据
        :param key:
        :param data:
        :return:
        """
        self.data.input.update({key: data})

    def append_output_data(self, key: str, data: Any) -> None:
        """
        添加节点输出数据
        :param key:
        :param data:
        :return:
        """
        self.data.output.update({key: data})

    def append_usage_data(self, data: Any) -> None:
        """
        添加大模型用户数据
        :param key:
        :param data:
        :return:
        """
        self.data.usage.total_tokens = data.get("total_tokens", 0)
        self.data.usage.question_tokens = data.get("question_tokens", 0)
        self.data.usage.prompt_tokens = data.get("prompt_tokens", 0)
        self.data.usage.completion_tokens = data.get("completion_tokens", 0)

    def append_config_data(self, data: Dict[str, Any]) -> None:
        """
        添加配置数据，主要于节点参数
        :param data:
        :return:
        """
        self.data.config.update(data)

    def _add_log(self, log_level: str, content: str) -> None:
        """
        添加日志
        :param log:
        :return:
        """
        log = {
            "level": log_level,
            "message": content,
            "time": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()),
        }
        self.logs.append(json.dumps(log, ensure_ascii=False))

    def add_info_log(self, log: str) -> None:
        """
        添加信息日志
        :param log:
        :return:
        """
        self._add_log("INFO", log)

    def add_error_log(self, log: str) -> None:
        """
        添加信息日志
        :param log:
        :return:
        """
        self._add_log("ERROR", log)
