"""
OpenAI-compatible types for Server-Sent Events (SSE) streaming.

This module defines data structures and response types that are compatible with
OpenAI's streaming API format, used for workflow execution streaming responses.
"""

import time
from typing import List, Literal, Optional, cast

from pydantic import BaseModel, Field


def current_time() -> int:
    """
    Get current Unix timestamp in seconds.

    :return: Current timestamp as integer
    """
    return int(time.time())


class GenerateUsage(BaseModel):
    """
    Token usage information for text generation.

    This class tracks the number of tokens used in prompts, completions,
    and total usage for billing and monitoring purposes.
    """

    completion_tokens: int = 0
    """Number of tokens in the generated completion."""

    prompt_tokens: int = 0
    """Number of tokens in the prompt."""

    total_tokens: int = 0
    """Total number of tokens used in the request (prompt + completion)."""

    def add(self, usage: "GenerateUsage") -> None:
        """
        Add another usage instance to this one.

        :param usage: Another GenerateUsage instance to add
        """
        self.completion_tokens += usage.completion_tokens
        self.prompt_tokens += usage.prompt_tokens
        self.total_tokens += usage.total_tokens


class NodeInfo(BaseModel):
    """
    Information about an executed workflow node.

    This class contains metadata about a node's execution including inputs,
    outputs, execution time, and completion status.
    """

    id: str = ""
    """Unique identifier of the node."""

    alias_name: str = ""
    """Human-readable name for the node."""

    finish_reason: Literal["stop", "interrupt", None] = None
    """Reason for node completion: 'stop' for normal completion, 'interrupt' for interruption, None for ongoing."""

    inputs: dict = {}
    """Input data passed to the node."""

    outputs: dict = {}
    """Output data produced by the node."""

    error_outputs: dict = {}
    """Error information if the node failed."""

    ext: Optional[dict] = None
    """Additional extension data for the node."""

    executed_time: float = Field(default=0.0)
    """Time taken to execute the node in seconds."""

    usage: Optional[GenerateUsage] = None
    """Token usage information for LLM nodes."""


class WorkflowStep(BaseModel):
    """
    Information about a workflow execution step.

    This class represents a single step in the workflow execution process,
    containing node information, sequence number, and progress tracking.
    """

    node: Optional[NodeInfo] = None
    """Information about the node being executed in this step."""

    seq: int = 0
    """Sequence number of the workflow step."""

    progress: float = Field(default=0.0)
    """Progress statistics for workflow execution (0.0 to 1.0)."""


class Delta(BaseModel):
    """
    Delta content for workflow responses.

    This class represents incremental content changes in streaming responses,
    including the main content and reasoning content.
    """

    role: str = "assistant"
    """Role of the entity generating the content."""

    content: str = ""
    """Main content of the response."""

    reasoning_content: str = ""
    """Reasoning or intermediate content for explainability."""


class Choice(BaseModel):
    """
    Choice object for OpenAI-compatible streaming responses.

    This class represents a single choice in the response, containing delta content
    and completion information.
    """

    delta: Delta
    """Delta content for this choice."""

    index: Optional[int] = None
    """Index of this choice in the response."""

    finish_reason: Literal["interrupt", "stop", None] = None
    """Reason for completion: 'stop' for normal completion, 'interrupt' for interruption, None for ongoing."""


class InterruptData(BaseModel):
    """
    Data structure for interrupt events.

    This class contains information about workflow interruption events,
    including event identification and context data.
    """

    event_id: str
    """Unique identifier for the interrupt event."""

    event_type: str = "interrupt"
    """Type of the event, defaults to 'interrupt'."""

    need_reply: bool = True
    """Whether a reply is needed for this interrupt event."""

    value: dict
    """Event-specific data and context information."""


class LLMGenerate(BaseModel):
    """
    Main response structure for LLM generation results.

    This class represents the complete response structure for workflow execution,
    compatible with OpenAI's streaming API format.
    """

    code: int = Field(default=0)
    """Status code returned by the model or workflow engine."""

    message: str = Field(default="Success")
    """Status message describing the result."""

    id: str
    """Session identifier (sid) for tracking the request."""

    created: int = Field(default_factory=current_time)
    """The Unix timestamp (in seconds) of when the chat completion was created."""

    workflow_step: Optional[WorkflowStep] = None
    """Workflow execution step information.
    This field is specific to workflow execution and not part of OpenAI's standard format.
    """

    choices: List[Choice]
    """List of response choices containing delta content."""

    usage: Optional[GenerateUsage] = None
    """Usage statistics for the completion request."""

    event_data: Optional[InterruptData] = None
    """Interrupt event data if the workflow was interrupted."""

    @staticmethod
    def _common(
        sid: str,
        code: int = 0,
        message: str = "Success",
        workflow_usage: Optional[GenerateUsage] = None,
        node_info: Optional[NodeInfo] = None,
        progress: float = 1,
        content: str = "",
        reasoning_content: str = "",
        finish_reason: Optional[str] = None,
    ) -> "LLMGenerate":
        """
        Build a common response result using static method.

        :param sid: Session or request unique identifier for tracking
        :param code: Status code, default 0 indicates success
        :param message: Status message description, default "Success"
        :param workflow_usage: Workflow execution usage statistics, such as token consumption
        :param node_info: Current node metadata information (e.g., node ID, type, etc.)
        :param progress: Execution progress, typically in range [0,1], default 1 indicates completion
        :param content: Main output content of the node or response, default empty string
        :param reasoning_content: Reasoning process or intermediate output for enhanced explainability, default empty string
        :param finish_reason: Completion reason (e.g., "stop", "length", etc.), default None
        :return: LLMGenerate instance with the specified parameters
        """
        workflow_step = WorkflowStep(
            node=node_info,
            seq=0,
            progress=progress,  # Frame sequence number is reassigned when dequeued
        )
        choice = Choice(
            delta=Delta(
                role="assistant", content=content, reasoning_content=reasoning_content
            ),
            index=0,
            finish_reason=cast(
                Literal["interrupt", "stop", None],
                (finish_reason if (finish_reason in ["interrupt", "stop"]) else None),
            ),
        )
        resp = LLMGenerate(
            code=code,
            message=message,
            id=sid,
            created=int(time.time()),
            workflow_step=workflow_step,
            choices=[choice],
        )
        if workflow_usage:
            resp.usage = workflow_usage
        return resp

    @staticmethod
    def _interrupt(
        sid: str,
        event_data: Optional[InterruptData] = None,
        code: int = 0,
        message: str = "Success",
        node_info: Optional[NodeInfo] = None,
        progress: float = 1,
        finish_reason: Optional[str] = None,
    ) -> "LLMGenerate":
        """
        Build interrupt event response result.

        :param sid: Session or request unique identifier for tracking
        :param event_data: Interrupt event related data structure containing context information
            that triggered the interrupt (e.g., node status, error information, etc.)
        :param code: Status code, default 0 indicates normal
        :param message: Status message description, default "Success"
        :param node_info: Information about the node that triggered the interrupt, e.g., node ID, type, etc.
        :param progress: Current execution progress, typically in range [0,1], default 1 indicates completion
        :param finish_reason: Interrupt/completion reason (e.g., "manual_interrupt", "error", etc.), default None
        :return: LLMGenerate instance for the interrupt event
        """
        workflow_step = WorkflowStep(
            node=node_info,
            seq=0,
            progress=progress,  # Frame sequence number is reassigned when dequeued
        )
        choice = Choice(
            delta=Delta(role="assistant", content="", reasoning_content=""),
            index=0,
            finish_reason=cast(
                Literal["interrupt", "stop", None],
                (finish_reason if (finish_reason in ["interrupt", "stop"]) else None),
            ),
        )
        resp = LLMGenerate(
            code=code,
            message=message,
            id=sid,
            created=int(time.time()),
            workflow_step=workflow_step,
            choices=[choice],
            event_data=event_data,
        )
        return resp

    @staticmethod
    def workflow_start(sid: str) -> "LLMGenerate":
        """
        Build workflow start event response result.

        :param sid: Session or request unique identifier for tracking workflow startup
        :return: LLMGenerate instance for workflow start event
        """
        return LLMGenerate._common(
            sid=sid,
            node_info=NodeInfo(
                id="flow_obj",
                finish_reason="stop",
                inputs={},
                outputs={},
                executed_time=0,
                usage=GenerateUsage(
                    prompt_tokens=0, completion_tokens=0, total_tokens=0
                ),
            ),
            progress=0,
        )

    @staticmethod
    def workflow_end(
        sid: str,
        workflow_usage: GenerateUsage,
        code: int = 0,
        message: str = "Success",
    ) -> "LLMGenerate":
        """
        Build workflow end event response result.

        :param sid: Session or request unique identifier for tracking workflow execution
        :param workflow_usage: Workflow execution usage statistics including resource consumption,
            call counts, token usage, and other information
        :param code: Status code, default 0 indicates success
        :param message: Status message description, default "Success"
        :return: LLMGenerate instance for workflow end event, containing execution results and statistics
        """
        return LLMGenerate._common(
            sid=sid,
            code=code,
            message=message,
            workflow_usage=workflow_usage,
            node_info=NodeInfo(
                id="flow_obj",
                finish_reason="stop",
                inputs={},
                outputs={},
                executed_time=0,
                usage=GenerateUsage(
                    prompt_tokens=0, completion_tokens=0, total_tokens=0
                ),
            ),
            progress=1,
            content="",
            reasoning_content="",
            finish_reason="stop",
        )

    @staticmethod
    def workflow_end_error(sid: str, code: int, message: str) -> "LLMGenerate":
        """
        Build workflow abnormal end event response result.

        :param sid: Session or request unique identifier for tracking workflow execution
        :param code: Error code for identifying the exception type
        :param message: Error description information explaining the specific exception cause
        :return: LLMGenerate instance for workflow error end event
        """
        llm_generate = LLMGenerate.workflow_end(
            sid=sid,
            workflow_usage=GenerateUsage(
                prompt_tokens=0, completion_tokens=0, total_tokens=0
            ),
            code=code,
            message=message,
        )
        if llm_generate.workflow_step:
            llm_generate.workflow_step.node = None
        return llm_generate

    @staticmethod
    def workflow_end_open_error(
        sid: str, code: int, message: str, stream: bool = False
    ) -> "LLMGenerate":
        """
        Build workflow abnormal end (open error) response result.

        :param sid: Session or request unique identifier for tracking workflow execution
        :param code: Error code for identifying the specific error type
        :param message: Error description information explaining the exception cause
        :param stream: Whether this is an error in streaming response scenario,
            True indicates error occurred during streaming push, default False
        :return: LLMGenerate instance for workflow open error end event
        """
        r = LLMGenerate.workflow_end(
            sid=sid,
            workflow_usage=GenerateUsage(
                prompt_tokens=0, completion_tokens=0, total_tokens=0
            ),
            code=code,
            message=message,
        )
        if r.workflow_step:
            r.workflow_step.node = None
        if not stream:
            r.workflow_step = None
        return r

    @staticmethod
    def node_start(
        sid: str,
        node_id: str,
        alias_name: str,
        progress: float,
        code: int = 0,
        message: str = "Success",
    ) -> "LLMGenerate":
        """
        Build node start event response result.

        :param sid: Session or request unique identifier for tracking the workflow
        :param node_id: Unique identifier of the node for locating specific node in workflow
        :param alias_name: Alias name of the node, typically used for frontend display or friendly identification
        :param progress: Current node execution progress, typically in range [0,1]
        :param code: Status code, default 0 indicates success
        :param message: Status message description, default "Success"
        :return: LLMGenerate instance for node start event
        """
        node_info = NodeInfo(
            id=node_id,
            alias_name=alias_name,
            finish_reason=None,
            inputs={},
            outputs={},
            executed_time=0,
        )
        return LLMGenerate._common(
            sid=sid,
            code=code,
            message=message,
            node_info=node_info,
            progress=progress,
        )

    @staticmethod
    def node_process(
        sid: str,
        node_id: str,
        alias_name: str,
        node_executed_time: float,
        node_ext: Optional[dict],
        progress: float,
        content: str,
        reasoning_content: str,
        code: int = 0,
        message: str = "Success",
    ) -> "LLMGenerate":
        """
        Build node execution process event response result.

        :param sid: Session or request unique identifier for tracking the workflow
        :param node_id: Unique identifier of the node for locating specific node in workflow
        :param alias_name: Alias name of the node, typically used for display or friendly identification
        :param node_executed_time: Time the node has been executing, in seconds, for performance statistics
        :param node_ext: Node extension information, storing additional context data or custom fields
        :param progress: Current node execution progress, typically in range [0,1]
        :param content: Main output result of the node
        :param reasoning_content: Reasoning process or intermediate results for enhanced explainability
        :param code: Status code, default 0 indicates success
        :param message: Status message description, default "Success"
        :return: LLMGenerate instance for node process event
        """
        node_info = NodeInfo(
            id=node_id,
            alias_name=alias_name,
            finish_reason=None,
            inputs={},
            outputs={},
            executed_time=node_executed_time,
            ext=node_ext,
        )
        return LLMGenerate._common(
            sid=sid,
            code=code,
            message=message,
            node_info=node_info,
            progress=progress,
            content=content,
            reasoning_content=reasoning_content,
        )

    @staticmethod
    def node_interrupt(
        sid: str,
        event_id: str,
        value: dict,
        node_id: str,
        alias_name: str,
        node_executed_time: float,
        node_ext: Optional[dict],
        progress: float,
        finish_reason: str,
        need_reply: bool = True,
        code: int = 0,
        message: str = "Success",
    ) -> "LLMGenerate":
        """
        Build node interrupt event response result.

        :param sid: Session or request unique identifier for tracking the workflow
        :param event_id: Unique identifier for the interrupt event to distinguish different interrupt sources
        :param value: Specific data of the interrupt event, containing trigger conditions or context information
        :param node_id: Unique identifier of the node for locating the interrupted node
        :param alias_name: Alias name of the node for more friendly display or identification
        :param node_executed_time: Time the node has been executing, in seconds
        :param node_ext: Node extension information, storing additional context data or custom fields
        :param progress: Current node execution progress, typically in range [0,1]
        :param finish_reason: Interrupt reason, explaining why the node was interrupted
        :param need_reply: Whether to send interrupt response to frontend or upstream, default True
        :param code: Status code, default 0 indicates success
        :param message: Status message description, default "Success"
        :return: LLMGenerate instance for node interrupt event
        """
        node_info = NodeInfo(
            id=node_id,
            alias_name=alias_name,
            finish_reason=cast(
                Literal["interrupt", "stop", None],
                (finish_reason if (finish_reason in ["interrupt", "stop"]) else None),
            ),
            inputs={},
            outputs={},
            executed_time=node_executed_time,
            ext=node_ext,
        )
        event_data = InterruptData(
            event_id=event_id,
            event_type="interrupt",
            need_reply=need_reply,
            value=value,
        )
        return LLMGenerate._interrupt(
            sid=sid,
            event_data=event_data,
            code=code,
            message=message,
            node_info=node_info,
            progress=progress,
            finish_reason=finish_reason,
        )
