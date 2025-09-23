from typing import Any, Dict

from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span


class StartNode(BaseNode):
    """
    Start node for workflow execution.

    This node represents the entry point of a workflow and is responsible
    for initializing the workflow execution by gathering input variables
    and setting up the execution context.
    """

    def sync_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Synchronous execution is not implemented for start nodes.

        :param variable_pool: Pool containing variables and their values
        :param span: Tracing span for monitoring and debugging
        :param event_log_node_trace: Optional node trace logging
        :param kwargs: Additional keyword arguments
        :return: NodeRunResult containing execution results
        :raises: NotImplementedError - synchronous execution not supported
        """
        raise NotImplementedError(
            "Synchronous execution not implemented for start nodes"
        )

    def get_node_config(self) -> Dict[str, Any]:
        """
        Get the configuration dictionary for the start node.

        Start nodes typically don't require complex configuration,
        so this returns an empty dictionary.

        :return: Empty configuration dictionary
        """
        return {}

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Execute the start node asynchronously.

        This method initializes the workflow by gathering input variables
        from the variable pool and setting up the execution context.

        :param variable_pool: Pool containing variables and their values
        :param span: Tracing span for monitoring and debugging
        :param event_log_node_trace: Optional node trace logging
        :param kwargs: Additional keyword arguments
        :return: NodeRunResult containing execution results
        """
        outputs: dict = {}  # Dictionary to store node output variables

        try:
            # Gather all output variables from the variable pool
            # These variables will be available to subsequent nodes in the workflow
            for key in self.output_identifier:
                outputs[key] = variable_pool.get_variable(
                    node_id=self.node_id, key_name=key, span=span
                )

            # Set special tracing attribute for agent user input if present
            # This helps with debugging and monitoring agent interactions
            if "AGENT_USER_INPUT" in outputs:
                span.set_attribute("AGENT_USER_INPUT", outputs["AGENT_USER_INPUT"])

            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=outputs,
                outputs={},
                node_id=self.node_id,
                node_type=self.node_type,
                alias_name=self.alias_name,
            )
        except Exception as e:
            # Record the exception in the tracing span for debugging
            span.record_exception(e)

            # Return a failed result with error details
            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.FAILED,
                error=CustomException(CodeEnum.StartNodeSchemaError, cause_error=e),
                inputs=outputs,  # Include any successfully gathered inputs
                outputs={},  # No outputs on failure
                node_id=self.node_id,
                node_type=self.node_type,
                alias_name=self.alias_name,
            )
