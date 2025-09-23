"""
Callback handler for workflow engine streaming and event management.

This module provides callback mechanisms for handling workflow execution events,
streaming responses, and managing asynchronous communication between workflow
nodes and external consumers.
"""

import asyncio
import json
import logging
import time
from asyncio import Queue
from dataclasses import dataclass
from typing import Any, Dict, Optional, Set

from workflow.cache.event_registry import EventRegistry
from workflow.consts.flow import FLOW_FINISH_REASON
from workflow.engine.callbacks.openai_types_sse import (
    Choice,
    Delta,
    GenerateUsage,
    LLMGenerate,
    NodeInfo,
    WorkflowStep,
)
from workflow.engine.entities.chains import Chains
from workflow.engine.entities.node_entities import NodeType
from workflow.engine.entities.output_mode import EndNodeOutputModeEnum
from workflow.engine.nodes.entities.node_run_result import NodeRunResult
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum


@dataclass
class ChatCallBackStreamResult:
    """
    Data structure for chat callback streaming results.

    This class encapsulates the result of a node execution in a streaming context,
    including the node identifier, generated content, and completion status.
    """

    node_id: str
    """Unique identifier of the executed node."""

    node_answer_content: LLMGenerate
    """Generated content from the node execution."""

    finish_reason: str = ""
    """Reason for node completion. 'stop' indicates normal completion, empty string otherwise."""


class ChatCallBacks:
    """
    Main callback handler for workflow execution events.

    This class manages the streaming of workflow execution events, handles node
    lifecycle callbacks, and coordinates with various queues for ordered output.
    It provides methods for different stages of workflow and node execution.
    """

    class Config:
        """Pydantic configuration for allowing arbitrary types."""

        arbitrary_types_allowed = True

    def __init__(
        self,
        sid: str,
        stream_queue: asyncio.Queue,
        end_node_output_mode: EndNodeOutputModeEnum,
        support_stream_node_ids: set,
        need_order_stream_result_q: asyncio.Queue,
        chains: Chains,
        event_id: str,
        flow_id: str,
    ):
        """
        Initialize the chat callback handler.

        :param sid: Session identifier for tracking the workflow execution
        :param stream_queue: Queue for streaming workflow execution results
        :param end_node_output_mode: Output mode configuration for end nodes
        :param support_stream_node_ids: Set of node IDs that support streaming output
        :param need_order_stream_result_q: Queue for ordered streaming results
        :param chains: Execution chains containing workflow node information
        :param event_id: Unique identifier for the current event
        :param flow_id: Unique identifier for the workflow
        """
        self.sid = sid
        self.generate_usage = GenerateUsage()
        self.stream_queue = stream_queue
        self.node_execute_start_time: dict = {}
        self.end_node_output_mode = end_node_output_mode
        self.support_stream_node_id_set = support_stream_node_ids
        self.order_stream_result_q = need_order_stream_result_q
        self.chains = chains
        self.resume_event = asyncio.Event()
        self.event_id = event_id
        self.flow_id = flow_id

        if chains:
            self.all_simple_paths_node_cnt = chains.get_all_simple_paths_node_cnt()

    def _get_node_progress(self, current_execute_node_id: str) -> float:
        """
        Calculate the current execution progress of the workflow.

        Progress calculation rules:
        - If a simple path is marked as inactive, all nodes in that path are considered completed
        - Otherwise, count the number of executed nodes in each simple path

        :param current_execute_node_id: ID of the currently executing node
        :return: Progress value between 0.0 and 1.0
        """
        completed_node_cnt = 0
        for simple_path in self.chains.master_chains:
            if simple_path.inactive.is_set():
                completed_node_cnt += len(simple_path.node_id_list)
            else:
                completed_node_cnt += simple_path.every_node_index.get(
                    current_execute_node_id, 0
                )
        return completed_node_cnt / self.all_simple_paths_node_cnt

    async def put_frame_into_queue(
        self, node_id: str, resp: LLMGenerate, finish_reason: str = ""
    ) -> None:
        """
        Add node response frame to appropriate queue for ordering.

        Routing logic:
        - Message nodes and end nodes are added to order_stream_result_q for sequencing
        - Other nodes are directly added to stream_queue

        :param node_id: Unique identifier of the node
        :param resp: Generated response from the node
        :param finish_reason: Reason for node completion
        """
        await self._interrupt_event_stream(resp)
        if node_id.split(":")[0] in [NodeType.MESSAGE.value, NodeType.END.value]:
            await self.order_stream_result_q.put(
                ChatCallBackStreamResult(
                    node_id=node_id,
                    node_answer_content=resp,
                    finish_reason=finish_reason,
                )
            )
        else:
            await self.stream_queue.put(resp)

    async def _interrupt_event_stream(self, resp: Any) -> None:
        """
        Handle event stream interruption by writing data to event registry.

        This method is called when the resume event is set, allowing the system
        to persist streaming data for later resumption.

        :param resp: Response data to be written to the event registry
        """
        if self.resume_event.is_set():
            event = EventRegistry().get_event(event_id=self.event_id)
            data = json.dumps(resp.dict(), ensure_ascii=False)
            await EventRegistry().write_resume_data(
                queue_name=event.get_workflow_q_name(),
                data=data,
                expire_time=event.timeout,
            )

    async def on_sparkflow_start(self) -> None:
        """
        Handle workflow start event.

        Creates and queues a workflow start response, then handles event stream interruption.
        """
        resp = LLMGenerate.workflow_start(self.sid)
        await self.stream_queue.put(resp)
        await self._interrupt_event_stream(resp)

    async def on_sparkflow_end(self, message: NodeRunResult) -> None:
        """
        Handle workflow end event.

        Creates and queues a workflow end response with usage statistics and error information.

        :param message: Final node run result containing execution summary
        """
        resp = LLMGenerate.workflow_end(
            sid=self.sid,
            workflow_usage=self.generate_usage,
            code=message.error.code if message.error else CodeEnum.Successes.code,
            message=message.error.message if message.error else CodeEnum.Successes.msg,
        )
        await self.stream_queue.put(resp)
        await self._interrupt_event_stream(resp)

    async def on_node_start(self, code: int, node_id: str, alias_name: str) -> None:
        """
        Handle node start event.

        Records the start time and creates a node start response with progress information.

        :param code: Status code for the node start operation
        :param node_id: Unique identifier of the starting node
        :param alias_name: Human-readable name for the node
        """
        self.node_execute_start_time[node_id] = time.time()
        resp = LLMGenerate.node_start(
            sid=self.sid,
            node_id=node_id,
            alias_name=alias_name,
            progress=self._get_node_progress(node_id),
            code=code,
            message="Success",
        )
        await self.put_frame_into_queue(node_id, resp)

    async def on_node_process(
        self,
        code: int,
        node_id: str,
        alias_name: str,
        message: str,
        reasoning_content: str = "",
    ) -> None:
        """
        Handle node processing event.

        Creates a node process response with execution time, progress, and content.
        Handles special cases for end nodes and error conditions.

        :param code: Status code for the node processing operation
        :param node_id: Unique identifier of the processing node
        :param alias_name: Human-readable name for the node
        :param message: Processing message or error content
        :param reasoning_content: Additional reasoning or intermediate content
        """
        ext = None
        if node_id.split(":")[0] == NodeType.END.value:
            ext = {"answer_mode": self.end_node_output_mode.value}

        content = message if code == 0 else ""  # If error occurs, content is empty
        if node_id.split(":")[0] == NodeType.END.value:
            if self.end_node_output_mode == EndNodeOutputModeEnum.VARIABLE_MODE:
                content = ""

        resp = LLMGenerate.node_process(
            sid=self.sid,
            node_id=node_id,
            alias_name=alias_name,
            node_executed_time=round(
                time.time() - self.node_execute_start_time.get(node_id, 0), 3
            ),
            node_ext=ext,
            progress=self._get_node_progress(node_id),
            content=content,
            reasoning_content=reasoning_content,
            code=code,
            message="Success" if code == 0 else message,
        )
        await self.put_frame_into_queue(node_id, resp)

    async def on_node_interrupt(
        self,
        event_id: str,
        value: dict,
        node_id: str,
        alias_name: str,
        code: int = 0,
        finish_reason: str = "interrupt",
        need_reply: bool = True,
    ) -> None:
        """
        Handle node interrupt event.

        Creates an interrupt response and sets the resume event flag for event stream handling.

        :param event_id: Unique identifier for the interrupt event
        :param value: Interrupt event data
        :param node_id: Unique identifier of the interrupted node
        :param alias_name: Human-readable name for the node
        :param code: Status code for the interrupt operation
        :param finish_reason: Reason for the interrupt
        :param need_reply: Whether a reply is needed for the interrupt
        """
        resp = LLMGenerate.node_interrupt(
            sid=self.sid,
            event_id=event_id,
            need_reply=need_reply,
            value=value,
            node_id=node_id,
            alias_name=alias_name,
            node_executed_time=round(
                time.time() - self.node_execute_start_time.get(node_id, 0), 3
            ),
            node_ext=None,
            progress=self._get_node_progress(node_id),
            code=code,
            message="Success",
            finish_reason=finish_reason,
        )
        await self.put_frame_into_queue(node_id, resp)
        if not self.resume_event.is_set():
            self.resume_event.set()

    async def _on_node_end_error(
        self, node_id: str, alias_name: str, error: CustomException
    ) -> None:
        """
        Handle node end error event.

        Creates an error response for a node that failed to complete successfully.

        :param node_id: Unique identifier of the failed node
        :param alias_name: Human-readable name for the node
        :param error: Exception containing error details
        """
        node_type = node_id.split(":")[0]
        resp = LLMGenerate(
            code=error.code,
            message=error.message,
            id=self.sid,
            workflow_step=WorkflowStep(
                node=NodeInfo(
                    id=node_id,
                    alias_name=alias_name,
                    finish_reason="stop",
                    executed_time=round(
                        time.time() - self.node_execute_start_time.get(node_id, 0), 3
                    ),
                    usage=(
                        GenerateUsage()
                        if node_type
                        in [NodeType.LLM.value, NodeType.DECISION_MAKING.value]
                        else None
                    ),
                ),
                progress=self._get_node_progress(node_id),
            ),
            choices=[
                Choice(
                    delta=Delta(),
                )
            ],
        )
        await self.put_frame_into_queue(node_id, resp, finish_reason=FLOW_FINISH_REASON)

    async def on_node_end(
        self,
        node_id: str,
        alias_name: str,
        message: Optional[NodeRunResult] = None,
        error: Optional[CustomException] = None,
    ) -> None:
        """
        Handle node end event.

        Processes the final result of a node execution, handling both success and error cases.
        Updates usage statistics and creates appropriate response based on node type.

        :param node_id: Unique identifier of the completed node
        :param alias_name: Human-readable name for the node
        :param message: Node execution result, None if execution failed
        :param error: Exception if node execution failed, None if successful
        """

        node_type = node_id.split(":")[0]
        ext: Dict[str, Any] = {}

        if error:
            return await self._on_node_end_error(node_id, alias_name, error)

        if not message:
            return await self._on_node_end_error(
                node_id,
                alias_name,
                CustomException(
                    CodeEnum.NodeRunErr,
                    "Node run error, please check the node configuration",
                ),
            )

        if message.error:
            return await self._on_node_end_error(
                node_id,
                alias_name,
                message.error,
            )

        if message.token_cost:
            self.generate_usage.add(message.token_cost)

        if node_type in [NodeType.LLM.value, NodeType.DECISION_MAKING.value]:
            if message.raw_output:
                ext = {"raw_output": message.raw_output}
            if node_type == NodeType.END.value:
                ext.update({"answer_mode": self.end_node_output_mode.value})

        content = message.node_answer_content
        if (
            node_type == NodeType.END.value
            and self.end_node_output_mode == EndNodeOutputModeEnum.VARIABLE_MODE
        ):
            content = json.dumps(
                message.outputs,
                ensure_ascii=False,
                separators=(",", ":"),
            )

        resp = LLMGenerate(
            id=self.sid,
            workflow_step=WorkflowStep(
                node=NodeInfo(
                    id=node_id,
                    alias_name=alias_name,
                    finish_reason="stop",
                    inputs=message.inputs,
                    outputs=message.outputs,
                    error_outputs=message.error_outputs,
                    ext=ext,
                    executed_time=round(
                        time.time() - self.node_execute_start_time.get(node_id, 0), 3
                    ),
                    usage=message.token_cost,
                ),
                progress=self._get_node_progress(
                    node_id
                ),  # Frame sequence number reassigned when dequeued
            ),
            choices=[
                Choice(
                    delta=Delta(
                        content=content,
                        reasoning_content=message.node_answer_reasoning_content,
                    ),
                )
            ],
        )
        await self.put_frame_into_queue(node_id, resp, finish_reason=FLOW_FINISH_REASON)


class ChatCallBackConsumer:
    """
    Consumer for callback function results with data organization.

    This class processes callback results, organizes data by node ID, and manages
    the flow of streaming results through various queues for ordered output.
    """

    # Queue for streaming results that need ordering (content may be out of order)
    need_order_stream_result_q: Queue
    # Queue for node IDs that support streaming output
    support_stream_node_id_queue: Queue
    # Structured data queues organized by node ID
    structured_data: Dict[str, Queue]

    def __init__(
        self,
        need_order_stream_result_q: Queue,
        support_stream_node_id_queue: Queue,
        structured_data: Dict[str, Queue],
    ):
        """
        Initialize the callback consumer.

        :param need_order_stream_result_q: Queue for unordered streaming results
        :param support_stream_node_id_queue: Queue for node IDs supporting streaming
        :param structured_data: Dictionary of queues organized by node ID
        """
        self.need_order_stream_result_q = need_order_stream_result_q
        self.support_stream_node_id_queue = support_stream_node_id_queue
        self.structured_data = structured_data
        self.support_stream_node_id_set: Set[str] = set()

    async def consume(self) -> None:
        """
        Main consumption loop for processing callback results.

        Continuously processes results from the need_order_stream_result_q,
        organizes them by node ID, and manages workflow completion detection.
        """
        while True:
            try:
                result: ChatCallBackStreamResult = (
                    await self.need_order_stream_result_q.get()
                )
                if result.node_id not in self.support_stream_node_id_set:
                    await self._add_node_in_q(result.node_id)
                if result.node_id not in self.structured_data:
                    self.structured_data[result.node_id] = Queue()
                await self.structured_data[result.node_id].put(result)
                # Workflow execution completed
                if (
                    result.node_id.split("::")[0] == NodeType.END.value
                    and result.finish_reason == FLOW_FINISH_REASON
                ):
                    await self._add_node_in_q(FLOW_FINISH_REASON)
                    break

            except asyncio.CancelledError:
                break
            except RuntimeError as e:
                if "Event loop is closed" in str(e):
                    break
                raise
            except Exception:
                logging.exception("ChatCallBackConsumer consume exception")
                continue

    async def _add_node_in_q(self, node_id: str) -> None:
        """
        Add a node ID to the support stream queue and tracking set.

        :param node_id: Unique identifier of the node to add
        """
        await self.support_stream_node_id_queue.put(node_id)
        self.support_stream_node_id_set.add(node_id)


class StructuredConsumer:
    """
    Consumer for structured streaming data with ordered output.

    This class processes structured data from various node queues and ensures
    ordered streaming output to the final stream queue.
    """

    # Queue for node IDs that support streaming output
    support_stream_node_id_queue: Queue

    # Structured data queues organized by node ID
    structured_data: Dict[str, Queue]

    # Final workflow streaming output queue
    stream_queue: Queue

    def __init__(
        self,
        support_stream_node_id_queue: Queue,
        structured_data: Dict[str, Queue],
        stream_queue: Queue,
        support_stream_node_id_set: set,
    ) -> None:
        """
        Initialize the structured consumer.

        :param support_stream_node_id_queue: Queue for node IDs supporting streaming
        :param structured_data: Dictionary of queues organized by node ID
        :param stream_queue: Final output queue for streaming results
        :param support_stream_node_id_set: Set of node IDs that support streaming
        """
        self.support_stream_node_id_queue = support_stream_node_id_queue
        self.structured_data = structured_data
        self.stream_queue = stream_queue
        self.support_stream_node_id_set = support_stream_node_id_set

    async def consume(self) -> None:
        """
        Main consumption loop for structured data processing.

        Processes node IDs from the support stream queue and handles ordered
        streaming output for each node.
        """
        while True:
            is_get = False
            try:
                node_id = await self.support_stream_node_id_queue.get()
                is_get = True
                # Check if queue consumption is complete
                if node_id == FLOW_FINISH_REASON:
                    break
                await self.order_stream_output(node_id)
            except Exception as e:
                if str(e).startswith("Event loop is closed"):
                    break
                logging.error(f"StructuredConsumer consume exception: {e}")
                raise e
            finally:
                if is_get:
                    self.support_stream_node_id_queue.task_done()

    async def order_stream_output(self, node_id: str) -> None:
        """
        Output streaming data in order for a specific node.

        Processes all results from a node's queue and outputs them sequentially
        to the final stream queue. Handles node completion and cleanup.

        :param node_id: Unique identifier of the node to process
        """
        try:
            q = self.structured_data.get(node_id)
            if q is None:
                raise Exception(f"structured data queue is None, node id is {node_id}")
            while True:
                result = await q.get()
                if isinstance(result, ChatCallBackStreamResult):
                    await self.stream_queue.put(result.node_answer_content)
                    if result.finish_reason == FLOW_FINISH_REASON:
                        self.support_stream_node_id_set.remove(node_id)
                        self.structured_data.pop(node_id)
                        break
                else:
                    raise Exception(
                        "need order stream result queue type error: ", result
                    )
        except Exception as e:
            logging.error(f"StructuredConsumer order stream output exception: {e}")
            raise e

    async def wait_for_completion(self) -> None:
        """
        Wait for all tasks in the support stream node ID queue to complete.

        This method blocks until all queued tasks have been processed.
        """
        await self.support_stream_node_id_queue.join()
