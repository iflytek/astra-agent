import os
from typing import Any

from pydantic import Field
from workflow.engine.entities.variable_pool import (
    VariablePool,
    schema_type_default_value,
    schema_type_map_python,
)
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.plugin_tool.link_client import Link
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span


class PluginNode(BaseNode):
    """
    Plugin node for executing external tools through the Link system.

    This node handles the execution of plugin tools by communicating with
    external services through the Link client. It supports both action inputs
    and business inputs for tool execution.
    """

    # Plugin tool identifier
    pluginId: str = Field(..., min_length=1)
    # Operation identifier for the specific tool operation
    operationId: str = Field(..., min_length=1)
    # Application identifier
    appId: str = Field(..., min_length=1)
    # Business input configuration for tool parameters
    businessInput: list = Field(default_factory=list)
    # Version of the plugin tool
    version: str = "V1.0"

    def get_business_input(self, action_input: Any) -> dict:
        """
        Extract business input parameters from action input using recursive traversal.

        This method recursively searches through the action input structure to find
        values for keys specified in the businessInput configuration. It handles
        nested dictionaries and lists to extract the required business parameters.

        :param action_input: The input data structure to search through
        :return: Dictionary containing extracted business input parameters
        """
        if not self.businessInput:
            return {}
        business_input = {}
        # Iterate through each business input key to extract
        for input_key in self.businessInput:
            # Use iterative approach with queue for breadth-first search
            iter_list: list = [action_input]
            while True:
                if not iter_list:
                    break
                iter_one = iter_list.pop(0)
                if isinstance(iter_one, dict):
                    # Check if current key matches the target business input key
                    for iter_key in iter_one:
                        if iter_key == input_key:
                            business_input.update({input_key: iter_one.get(input_key)})
                            break
                        # Add nested structures to queue for further processing
                        elif isinstance(iter_one.get(iter_key), (dict, list)):
                            iter_list.append(iter_one.get(iter_key))
                elif isinstance(iter_one, list):
                    # Add all nested structures from list to queue
                    iter_list.extend(
                        [
                            content
                            for content in iter_one
                            if isinstance(content, (dict, list))
                        ]
                    )
        return business_input

    @property
    def run_s(self) -> WorkflowNodeExecutionStatus:
        """
        Get the success execution status.

        :return: SUCCEEDED status for successful execution
        """
        return WorkflowNodeExecutionStatus.SUCCEEDED

    @property
    def run_f(self) -> WorkflowNodeExecutionStatus:
        """
        Get the failure execution status.

        :return: FAILED status for failed execution
        """
        return WorkflowNodeExecutionStatus.FAILED

    async def execute(
        self, variable_pool: VariablePool, span: Span, **kwargs: Any
    ) -> NodeRunResult:
        """
        Execute the plugin node by calling the external tool through Link system.

        This method handles the complete execution flow including:
        1. Setting up the Link client
        2. Preparing input parameters
        3. Executing the tool operation
        4. Processing and validating outputs
        5. Handling errors and exceptions

        :param variable_pool: Pool containing workflow variables
        :param span: Tracing span for monitoring execution
        :param kwargs: Additional keyword arguments
        :return: NodeRunResult containing execution status and outputs
        """
        try:
            event_log_node_trace = kwargs.get("event_log_node_trace")
            # Initialize Link client for tool communication
            link = Link(
                app_id=self.appId,
                tool_ids=[self.pluginId],
                get_url=os.getenv("PLUGIN_GET_URL", ""),
                run_url=os.getenv("PLUGIN_RUN_URL", ""),
                version=self.version,
            )
            action_inputs = {}
            inputs, outputs = {}, {}
            # Collect input variables from variable pool
            for identifier in self.input_identifier:
                action_inputs[identifier] = variable_pool.get_variable(
                    node_id=self.node_id, key_name=identifier, span=span
                )
            span.add_info_events({"action_input": f"{action_inputs}"})
            inputs = action_inputs
            status = self.run_s

            # Find and execute the matching tool operation
            for tool in link.tools:
                if tool.operation_id == self.operationId:
                    business_input = self.get_business_input(action_inputs)
                    # Execute the tool operation
                    res = await tool.run(
                        action_inputs,
                        business_input,
                        span,
                        event_log_node_trace=event_log_node_trace,
                    )
                    # Process and validate output variables
                    for output_key in self.output_identifier:
                        # Handle missing output variables by providing default values
                        var_type = variable_pool.get_output_schema(
                            node_id=self.node_id, key_name=output_key
                        ).get("type", "")
                        if output_key not in res:
                            span.add_info_events(
                                {"null value occur": f"{output_key} does not exist"}
                            )
                            res[output_key] = schema_type_default_value[var_type]
                        else:
                            span.add_info_events(
                                {
                                    "result type": f"{output_key}'s type is {type(res[output_key])}"
                                }
                            )
                            # Validate output type and provide default if type mismatch
                            if (
                                type(res[output_key])
                                not in schema_type_map_python[var_type]
                            ):
                                res[output_key] = schema_type_default_value[var_type]
                        outputs.update({output_key: res[output_key]})
                    break
            else:
                # Handle case where plugin operation is not found
                span.add_error_event(f"Error : plugin not found: {self.operationId}")
                status = self.run_f

            # Return successful execution result
            return NodeRunResult(
                status=status,
                inputs=inputs,
                outputs=outputs,
                node_id=self.node_id,
                node_type=self.node_type,
                alias_name=self.alias_name,
            )
        except Exception as e:
            # Handle execution errors and return failure result
            status = self.run_f
            span.record_exception(e)
            return NodeRunResult(
                status=status,
                error=CustomException(
                    CodeEnum.PLUGIN_NODE_EXECUTION_ERROR,
                    cause_error=e,
                ),
                node_id=self.node_id,
                node_type=self.node_type,
                alias_name=self.alias_name,
            )

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronous execution method with tracing and logging support.

        This method wraps the main execute method with additional tracing
        and logging capabilities for monitoring and debugging purposes.

        :param variable_pool: Pool containing workflow variables
        :param span: Tracing span for monitoring execution
        :param event_log_node_trace: Optional node trace logging
        :param kwargs: Additional keyword arguments
        :return: NodeRunResult containing execution status and outputs
        """
        with span.start(
            func_name="async_execute", add_source_function_name=True
        ) as span_context:
            # Log plugin configuration for debugging
            if event_log_node_trace:
                event_log_node_trace.append_config_data(
                    {"pluginId": self.pluginId, "operationId": self.operationId}
                )
            return await self.execute(
                variable_pool,
                span_context,
                event_log_node_trace=event_log_node_trace,
                **kwargs,
            )
