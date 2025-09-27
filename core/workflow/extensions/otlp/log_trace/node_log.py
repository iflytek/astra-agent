"""
Node-level logging functionality for workflow execution tracking.

This module provides data structures and methods for logging individual
node execution details, including timing, data flow, and performance metrics.
"""

import json
import time
import uuid
from typing import Any, Dict, Set

from pydantic import BaseModel, Field

from workflow.extensions.otlp.log_trace.base import Usage


class Data(BaseModel):
    """
    Workflow node data container.

    This class encapsulates all data associated with a workflow node execution,
    including input parameters, output results, configuration settings, and usage statistics.
    """

    input: Dict[str, Any] = {}
    output: Dict[str, Any] = {}
    config: Dict[str, Any] = {}
    usage: Usage = Usage()


class NodeLog(BaseModel):
    """
    Workflow node execution log.

    This class represents a comprehensive log entry for a single workflow node execution,
    tracking timing, performance metrics, data flow, and execution status.
    """

    # Unique log identifier
    id: str = Field(default_factory=lambda: uuid.uuid4().hex)
    sid: str = ""

    # Legacy node fields (deprecated but kept for compatibility)
    node_id: str = ""  # Node ID (deprecated field, kept for compatibility)
    node_type: str = ""  # Node type (deprecated field, kept for compatibility)
    node_name: str = ""  # Node name (deprecated field, kept for compatibility)

    # Function identification fields
    func_id: str = ""  # Function ID
    func_type: str = ""  # Function type
    func_name: str = ""  # Function name

    # Execution flow tracking
    next_log_ids: Set[str] = set()  # IDs of subsequent log entries

    # Timing information
    start_time: int = Field(default_factory=lambda: int(time.time() * 1000))
    end_time: int = Field(default_factory=lambda: int(time.time() * 1000))
    duration: int = 0
    first_frame_duration: int = (
        -1
    )  # First frame latency for streaming APIs (-1 if not applicable)
    node_first_cost_time: float = -1  # First frame cost time for external LLM models

    # Execution details
    llm_output: str = ""
    running_status: bool = True
    data: Data = Data()  # Node data container
    logs: list[str] = []  # Execution logs

    def __init__(
        self,
        sid: str,
        func_id: str = "",
        func_name: str = "",
        func_type: str = "",
        **kwargs: Any,
    ) -> None:
        """
        Initialize a new NodeLog instance.

        :param sid: Session ID for the workflow execution
        :param func_id: Function ID (defaults to node_id if not provided)
        :param func_name: Function name (defaults to node_name if not provided)
        :param func_type: Function type (defaults to node_type if not provided)
        :param kwargs: Additional keyword arguments including legacy node fields
        """
        node_id = kwargs.get("node_id", "")
        node_type = kwargs.get("node_type", "")
        node_name = kwargs.get("node_name", "")

        # Use provided function fields or fall back to legacy node fields
        func_id = func_id if func_id else node_id
        func_name = func_name if func_name else node_name
        func_type = func_type if func_type else node_type

        super().__init__(
            sid=sid, func_id=func_id, func_name=func_name, func_type=func_type, **kwargs
        )

    def set_next_node_id(self, next_id: str) -> None:
        """
        Add the ID of the next node in the execution flow.

        :param next_id: ID of the next node to be executed
        """
        self.next_log_ids.add(next_id)

    def set_first_frame_duration(self) -> None:
        """
        Calculate and set the first frame duration for streaming APIs.

        This method calculates the time elapsed from start to the first frame
        and stores it in milliseconds.
        """
        self.first_frame_duration = int(time.time() * 1000) - self.start_time

    def set_node_first_cost_time(self, cost_time: float) -> None:
        """
        Set the first frame cost time for external LLM models.

        :param cost_time: Cost time in seconds for the first frame response
        """
        self.node_first_cost_time = cost_time

    def set_start(self) -> None:
        """
        Set the start time for node execution.

        Updates the start_time field with the current timestamp in milliseconds.
        """
        self.start_time = int(time.time() * 1000)

    def set_end(self) -> None:
        """
        Mark the end of node execution and calculate duration.

        Sets the end_time to current timestamp and calculates the total
        execution duration in milliseconds.
        """
        self.end_time = int(time.time() * 1000)
        self.duration = self.end_time - self.start_time

    def append_input_data(self, key: str, data: Any) -> None:
        """
        Add input data to the node log.

        :param key: Key identifier for the input data
        :param data: Input data value to be stored
        """
        # self.data.input += "\n" + json.dumps({key: data}, ensure_ascii=False)
        self.data.input.update({key: data})

    def append_output_data(self, key: str, data: Any) -> None:
        """
        Add output data to the node log.

        :param key: Key identifier for the output data
        :param data: Output data value to be stored
        """
        # self.data.output += "\n" + json.dumps({key: data}, ensure_ascii=False)
        self.data.output.update({key: data})

    def append_usage_data(self, data: Any) -> None:
        """
        Add LLM usage statistics to the node log.

        :param data: Dictionary containing token usage statistics
        """
        self.data.usage.total_tokens = data.get("total_tokens", 0)
        self.data.usage.question_tokens = data.get("question_tokens", 0)
        self.data.usage.prompt_tokens = data.get("prompt_tokens", 0)
        self.data.usage.completion_tokens = data.get("completion_tokens", 0)

    def append_config_data(self, data: Dict[str, Any]) -> None:
        """
        Add configuration data to the node log, primarily for node parameters.

        :param data: Dictionary containing configuration parameters
        """
        self.data.config.update(data)
        # for key, value in data.items():
        #     if not isinstance(value, str):
        #         self.data.config.update({key: json.dumps(value, ensure_ascii=False)})

    def _add_log(self, log_level: str, content: str) -> None:
        """
        Add a log entry with specified level and content.

        :param log_level: Log level (e.g., INFO, ERROR, DEBUG)
        :param content: Log message content
        """
        log = {
            "level": log_level,
            "message": content,
            "time": time.strftime("%Y-%m-%d %H:%M:%S", time.localtime()),
        }
        self.logs.append(json.dumps(log, ensure_ascii=False))

    def add_info_log(self, log: str) -> None:
        """
        Add an informational log entry.

        :param log: Information log message
        """
        self._add_log("INFO", log)

    def add_error_log(self, log: str) -> None:
        """
        Add an error log entry.

        :param log: Error log message
        """
        self._add_log("ERROR", log)
