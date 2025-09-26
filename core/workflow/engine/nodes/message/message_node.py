"""
Message node implementation for workflow engine.

This module provides the MessageNode class which handles intermediate message output
during workflow execution. It supports template-based message generation and streaming output.
"""

from typing import Any, Dict, Optional

from pydantic import Field

from workflow.engine.callbacks.callback_handler import ChatCallBacks
from workflow.engine.entities.msg_or_end_dep_info import MsgOrEndDepInfo
from workflow.engine.entities.node_running_status import NodeRunningStatus
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseOutputNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.util.prompt import prompt_template_replace
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span


class MessageNode(BaseOutputNode):
    """
    Message node for intermediate process message output.

    This node supports template-based message generation and can output messages
    during workflow execution. It inherits from BaseOutputNode and provides
    streaming output capabilities.
    """

    template: str = Field(...)
    startFrameEnabled: Optional[bool] = None

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronously execute the message node.

        This method processes the message template, handles streaming output,
        and manages node dependencies and callbacks.

        :param variable_pool: Pool of variables available to the node
        :param span: Tracing span for observability
        :param event_log_node_trace: Optional node trace logging instance
        :param kwargs: Additional keyword arguments including callbacks and dependencies
        :return: NodeRunResult containing execution results and timing information
        """
        # Initialize execution variables
        callbacks: ChatCallBacks = kwargs.get("callbacks", None)
        content = ""
        reasoning_content = ""
        inputs = {}

        try:
            # Extract dependency information and node run status
            msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo] = kwargs.get(
                "msg_or_end_node_deps", {}
            )
            node_run_status: Dict[str, NodeRunningStatus] = kwargs.get(
                "node_run_status", {}
            )

            # Wait for prerequisite nodes to complete
            is_run = await self.await_pre_output_node_complete(
                msg_or_end_node_deps=msg_or_end_node_deps,
                node_run_status=node_run_status,
            )

            if not is_run:
                # Node logic runs but actual execution is cancelled
                return NodeRunResult(
                    status=WorkflowNodeExecutionStatus.CANCELLED,
                    inputs={},
                    outputs={},
                    node_answer_content="",
                    node_id=self.node_id,
                    alias_name=self.alias_name,
                    node_type=self.node_type,
                )

            # Notify callbacks that node execution has started
            await callbacks.on_node_start(
                code=0, node_id=self.node_id, alias_name=self.alias_name
            )

            # Process streaming output message
            output_node_frame_data = await self.deal_output_stream_msg(
                variable_pool=variable_pool,
                template=self.template,
                reasoning_template="",
                callbacks=callbacks,
                node_run_status=node_run_status,
                span=span,
            )
            if output_node_frame_data:
                content = output_node_frame_data.content
                reasoning_content = output_node_frame_data.reasoning_content

            # Wait for all dependent message nodes to complete
            for dep_msg_node in msg_or_end_node_deps[self.node_id].data_dep:
                await node_run_status[dep_msg_node].complete.wait()

            # Collect input variables from the variable pool
            for input_key in self.input_identifier:
                val = variable_pool.get_variable(
                    node_id=self.node_id, key_name=input_key, span=span
                )
                inputs[input_key] = val

            # Replace template variables with actual values
            prompt_template = prompt_template_replace(
                input_identifier=self.input_identifier,
                _prompt_template=self.template,
                node_id=self.node_id,
                variable_pool=variable_pool,
                span_context=span,
            )

            # Create successful execution result
            node_run_result = NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=inputs,
                outputs={},
                node_answer_content=(
                    prompt_template if not self.streamOutput else content
                ),
                node_answer_reasoning_content=reasoning_content,
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )

            # Notify callbacks that node execution has completed
            await callbacks.on_node_end(
                node_id=self.node_id,
                alias_name=self.alias_name,
                message=node_run_result,
            )
            return node_run_result
        except CustomException as err:
            # Handle custom exceptions with specific error codes
            span.record_exception(err)
            run_result = NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=err,
            )
            return run_result
        except Exception as err:
            # Handle unexpected exceptions
            span.record_exception(err)
            node_run_result = NodeRunResult(
                status=WorkflowNodeExecutionStatus.FAILED,
                inputs=inputs,
                error=CustomException(
                    CodeEnum.MESSAGE_NODE_EXECUTION_ERROR,
                    cause_error=err,
                ),
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )
            return node_run_result
