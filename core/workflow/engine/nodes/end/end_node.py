from typing import Any, Dict

from pydantic import Field

from workflow.engine.callbacks.callback_handler import ChatCallBacks
from workflow.engine.entities.msg_or_end_dep_info import MsgOrEndDepInfo
from workflow.engine.entities.node_running_status import NodeRunningStatus
from workflow.engine.entities.output_mode import EndNodeOutputModeEnum
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


class EndNode(BaseOutputNode):
    """
    End node for workflow execution.

    This node represents the exit point of a workflow and is responsible
    for finalizing the workflow execution, processing output templates,
    and returning the final results.

    :param template: Template for generating the final output content
    :param reasoningTemplate: Template for generating reasoning content
    :param outputMode: Mode for output generation (prompt mode or direct mode)
    """

    template: str = Field(default="")
    reasoningTemplate: str = Field(default="")
    outputMode: int

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Execute the end node asynchronously.

        This method finalizes the workflow execution by processing output templates,
        gathering final results, and returning the completed workflow output.

        :param variable_pool: Pool containing variables and their values
        :param span: Tracing span for monitoring and debugging
        :param event_log_node_trace: Optional node trace logging
        :param kwargs: Additional keyword arguments including:
            - msg_or_end_node_deps: Dependencies on message or end nodes
            - node_run_status: Status tracking for all nodes
            - callbacks: Callback handlers for workflow events
        :return: NodeRunResult containing final execution results
        """
        # Initialize execution variables
        content = ""
        reasoning_content = ""
        inputs: dict = {}
        outputs: dict = {}
        prompt_template = ""
        try:
            # Extract execution context from kwargs
            msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo] = kwargs.get(
                "msg_or_end_node_deps", {}
            )
            node_run_status: Dict[str, NodeRunningStatus] = kwargs.get(
                "node_run_status", {}
            )

            # Wait for all prerequisite output nodes to complete
            await self.await_pre_output_node_complete(
                msg_or_end_node_deps=msg_or_end_node_deps,
                node_run_status=node_run_status,
            )
            callbacks: ChatCallBacks = kwargs.get("callbacks", None)

            # Notify callbacks that node execution has started
            await callbacks.on_node_start(
                code=0, node_id=self.node_id, alias_name=self.alias_name
            )

            # Process output in prompt mode if configured
            if self.outputMode == EndNodeOutputModeEnum.PROMPT_MODE.value:
                output_node_frame_data = await self.deal_output_stream_msg(
                    variable_pool=variable_pool,
                    template=self.template,
                    reasoning_template=self.reasoningTemplate,
                    callbacks=callbacks,
                    node_run_status=node_run_status,
                    span=span,
                )
                if output_node_frame_data:
                    content = output_node_frame_data.content
                    reasoning_content = output_node_frame_data.reasoning_content

            # Wait for all dependent message nodes to complete
            for dep_msg_node in msg_or_end_node_deps[self.node_id].data_dep:
                if dep_msg_node not in node_run_status:
                    raise CustomException(
                        err_code=CodeEnum.END_NODE_SCHEMA_ERROR,
                        cause_error=f"Node {dep_msg_node} not found in node_run_status",
                    )
                await node_run_status[dep_msg_node].complete.wait()

            # Collect output variables from the variable pool
            for end_input in self.input_identifier:
                outputs.update(
                    {
                        end_input: variable_pool.get_variable(
                            node_id=self.node_id, key_name=end_input, span=span
                        )
                    }
                )

            # Process templates for prompt mode output
            reasoning_template = ""
            if self.outputMode == EndNodeOutputModeEnum.PROMPT_MODE.value:
                # Replace variables in reasoning template
                reasoning_template = prompt_template_replace(
                    input_identifier=self.input_identifier,
                    _prompt_template=self.reasoningTemplate,
                    variable_pool=variable_pool,
                    node_id=self.node_id,
                    span_context=span,
                )
                # Replace variables in main template
                prompt_template = prompt_template_replace(
                    input_identifier=self.input_identifier,
                    _prompt_template=self.template,
                    variable_pool=variable_pool,
                    node_id=self.node_id,
                    span_context=span,
                )
            # Create successful execution result
            node_run_result = NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=inputs,
                outputs=outputs,
                node_answer_content=(
                    prompt_template if not self.streamOutput else content
                ),
                node_answer_reasoning_content=(
                    reasoning_template if not self.streamOutput else reasoning_content
                ),
                node_id=self.node_id,
                node_type=self.node_type,
                alias_name=self.alias_name,
            )
            # Notify callbacks that node execution has completed
            await callbacks.on_node_end(
                node_id=self.node_id,
                alias_name=self.alias_name,
                message=node_run_result,
            )

            return node_run_result
        except CustomException as err:
            # Handle custom exceptions with proper error tracking
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
            # Handle unexpected exceptions with generic error handling
            span.record_exception(err)
            run_result = NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=CustomException(
                    CodeEnum.END_NODE_EXECUTION_ERROR,
                    cause_error=err,
                ),
            )
            return run_result
