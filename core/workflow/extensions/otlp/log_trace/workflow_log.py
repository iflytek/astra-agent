"""
Workflow-level logging functionality for comprehensive execution tracking.

This module provides data structures and methods for logging entire workflow
executions, including status tracking, performance metrics, and data management.
"""

import json
import os
import time
import uuid
from typing import Any, Dict, List

from pydantic import BaseModel, Field
from workflow.engine.entities.node_entities import NodeType
from workflow.extensions.middleware.getters import get_oss_service
from workflow.extensions.otlp.log_trace.base import Usage
from workflow.extensions.otlp.log_trace.node_log import NodeLog


class Status(BaseModel):
    """
    Execution status information.

    This class represents the status of workflow execution,
    including status code and descriptive message.
    """

    code: int = 0
    message: str = ""


class WorkflowLog(BaseModel):
    """
    Comprehensive workflow execution log.

    This class represents a complete log entry for an entire workflow execution,
    tracking all aspects from timing and performance to individual node traces.
    """

    # Service and session identification
    service_id: str  # Service ID, used for flow_id, bot_id, mcp_id placement
    flow_id: str = Field(
        default="", description="Workflow ID (deprecated field)"
    )  # Workflow ID (deprecated field, kept for compatibility)
    sid: str  # Session ID
    app_id: str = ""
    uid: str = ""
    bot_id: str = Field(default="", description="Assistant ID (deprecated field)")
    chat_id: str = ""
    sub: str  # Business service classification
    caller: str = ""  # Business call source for statistics and source differentiation
    log_caller: str = ""  # Function that reports this log

    # Input and output data
    question: str = ""
    answer: str = ""

    # Timing information
    start_time: int = Field(default_factory=lambda: int(time.time() * 1000))
    end_time: int = Field(default_factory=lambda: int(time.time() * 1000))
    duration: int = 0
    first_frame_duration: float = -1.0  # End-to-end first frame latency

    # Business attributes and metadata
    srv: Dict[str, str] = {}  # Upper-level business attributes (to be determined)
    srv_tag: Dict[str, str] = {}  # Upper-level business attributes (to be determined)
    status: Status = Status()  # Execution status
    usage: Usage = Usage()  # Token usage statistics
    version: str = "v2.0.0"
    trace: List[NodeLog] = Field(default_factory=list)  # Node execution traces

    class Config:
        arbitrary_types_allowed = True

    def __init__(self, sid: str, sub: str = "workflow", **kwargs: Any) -> None:
        """
        Initialize a new WorkflowLog instance.

        :param sid: Session ID for the workflow execution
        :param sub: Business service classification (defaults to "workflow")
        :param kwargs: Additional keyword arguments including service identification
        """
        flow_id = kwargs.get("flow_id", "")
        bot_id = kwargs.get("bot_id", "")

        # Backward compatibility: flow_id and bot_id can be passed via service_id
        if "service_id" not in kwargs:
            kwargs["service_id"] = bot_id if sub in ["SparkAgent"] else flow_id
        else:
            if not flow_id:
                kwargs["flow_id"] = kwargs["service_id"]
            if not bot_id:
                kwargs["bot_id"] = kwargs["service_id"]

        super().__init__(sid=sid, sub=sub, **kwargs)

    def add_q(self, question: str) -> None:
        """
        Add question input to the workflow log.

        :param question: Input question string
        """
        self.question = question

    def add_a(self, answer: str) -> None:
        """
        Add answer output to the workflow log.

        :param answer: Output answer string
        """
        self.answer = answer

    def add_first_frame_duration(self, first_frame_duration: int) -> None:
        """
        Set the first frame duration for the workflow execution.

        :param first_frame_duration: First frame duration in milliseconds
        """
        self.first_frame_duration = first_frame_duration

    def add_srv(self, key: str, value: str) -> None:
        """
        Add service attribute to both srv and srv_tag dictionaries.

        :param key: Attribute key
        :param value: Attribute value
        """
        self.srv[key] = value
        self.srv_tag[key] = value

    def set_end(self) -> None:
        """
        Mark the end of workflow execution and calculate aggregated metrics.

        Sets the end time, calculates total duration, and aggregates
        token usage statistics from all node logs.
        """
        self.end_time = int(time.time() * 1000)
        self.duration = self.end_time - self.start_time
        # Aggregate usage statistics from all node logs
        for i, node_log in enumerate(self.trace):
            self.usage.total_tokens += node_log.data.usage.total_tokens
            self.usage.prompt_tokens += node_log.data.usage.prompt_tokens
            self.usage.question_tokens += node_log.data.usage.question_tokens
            self.usage.completion_tokens += node_log.data.usage.completion_tokens

    def set_status(self, code: int, message: str) -> None:
        """
        Set the execution status of the workflow.

        :param code: Status code indicating execution result
        :param message: Descriptive message for the status
        """
        self.status.code = code
        self.status.message = message

    def add_node_log(self, node_logs: list[NodeLog]) -> None:
        """
        Add node logs to the workflow trace and calculate first frame duration.

        This method processes node logs and determines the first frame duration
        based on the first message or end node encountered.

        :param node_logs: List of NodeLog instances to add to the trace
        """
        if not node_logs:
            return

        # Calculate first frame duration if not already set
        # Rule: If the first message node is encountered, set first frame duration
        # as (message node start time - workflow start time)
        if self.first_frame_duration == -1:
            for i, node_log in enumerate(node_logs):
                node_type = node_log.node_id.split(":")[0]
                if (
                    node_type == NodeType.MESSAGE.value
                    or node_type == NodeType.END.value
                ):
                    self.first_frame_duration = node_log.start_time - self.start_time
                    break

        self.trace.extend(node_logs)

    def add_func_log(self, node_logs: list[NodeLog]) -> None:
        """
        Add function logs to the workflow trace.

        This is an alias for add_node_log for backward compatibility.

        :param node_logs: List of NodeLog instances to add to the trace
        """
        self.add_node_log(node_logs)

    def to_json(self) -> str:
        """
        Convert the workflow log to JSON string.

        Large values (>5KB) are uploaded to object storage and replaced
        with storage references in the JSON output.

        :return: JSON string representation of the workflow log
        """
        import sys

        def is_large_string(s: str, limit: int = 5 * 1024) -> bool:
            """
            Check if a string exceeds the size limit for direct JSON inclusion.

            :param s: String to check
            :param limit: Size limit in bytes (default: 5KB)
            :return: True if string exceeds limit, False otherwise
            """
            return isinstance(s, str) and sys.getsizeof(s.encode("utf-8")) > limit

        def process_data(data: dict) -> Any:
            """
            Recursively process data structure to handle large strings.

            :param data: Data structure to process
            :return: Processed data with large strings uploaded to OSS
            """
            if isinstance(data, dict):
                return {k: process_data(v) for k, v in data.items()}
            elif isinstance(data, list):
                return [process_data(item) for item in data]
            elif isinstance(data, str):
                if is_large_string(data):
                    return get_oss_service().upload_file(
                        f"{uuid.uuid4().hex}.txt",
                        data.encode("utf-8"),
                        bucket_name=os.getenv("OSS_BUCKET_NAME", "test"),
                    )
                else:
                    return data
            else:
                return data

        result = process_data(self.dict())

        def json_fallback(obj: Any) -> Any:
            """
            Fallback function for JSON serialization of unsupported types.

            :param obj: Object to serialize
            :return: JSON-serializable representation of the object
            """
            if isinstance(obj, set):
                return list(obj)

        return json.dumps(result, ensure_ascii=False, default=json_fallback)
