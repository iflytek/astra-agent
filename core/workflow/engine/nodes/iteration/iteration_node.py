import asyncio
import copy
import traceback
from typing import Any, Dict

from workflow.engine.callbacks.callback_handler import ChatCallBacks
from workflow.engine.entities.chains import Chains
from workflow.engine.entities.node_entities import NodeType
from workflow.engine.entities.node_running_status import NodeRunningStatus
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.util.prompt import prompt_template_replace
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.log_trace.workflow_log import WorkflowLog
from workflow.extensions.otlp.trace.span import Span


class IterationNode(BaseNode):
    """
    Iteration node that executes a workflow subgraph for each item in a batch.

    This node processes batch data by running a complete workflow iteration
    for each item in the input batch, collecting and aggregating results.
    """

    # Node ID of the first node in the workflow subgraph within this iteration
    IterationStartNodeId: str

    def get_node_config(self) -> Dict[str, Any]:
        """
        Get the configuration parameters for this iteration node.

        :return: Dictionary containing the iteration start node ID
        """
        return {"IterationStartNodeId": self.IterationStartNodeId}

    def sync_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Synchronous execution method (not implemented for iteration nodes).

        :param variable_pool: Pool of variables for the workflow execution
        :param span: Tracing span for monitoring and debugging
        :param event_log_node_trace: Optional node-level event logging
        :param kwargs: Additional keyword arguments including callback methods
        :return: NodeRunResult containing execution status and outputs
        """
        raise NotImplementedError

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronously execute the iteration node by processing batch data.

        This method processes each item in the input batch by running a complete
        workflow iteration, then aggregates the results from all iterations.

        :param variable_pool: Pool of variables for the workflow execution
        :param span: Tracing span for monitoring and debugging
        :param event_log_node_trace: Optional node-level event logging
        :param kwargs: Additional keyword arguments including:
            - callbacks: ChatCallBacks for handling callbacks
            - event_log_trace: WorkflowLog for event logging
            - node_run_status: Dict tracking node running status
            - iteration_engine: Dictionary of workflow engines for iteration
        :return: NodeRunResult containing execution status and aggregated outputs
        """

        with span.start(
            func_name="async_execute", add_source_function_name=True
        ) as span_context:
            callbacks: ChatCallBacks = kwargs.get("callbacks", {})
            event_log_trace: WorkflowLog = kwargs.get("event_log_trace", {})
            node_run_status: Dict[str, NodeRunningStatus] = kwargs.get(
                "node_run_status", {}
            )
            node_run_status[self.node_id].processing.set()
            try:
                iteration_one_engine = kwargs.get("iteration_engine", {})[
                    self.IterationStartNodeId
                ]
                source_iteration_chains = (
                    iteration_one_engine.engine_ctx.chains.iteration_chains[
                        self.node_id
                    ]
                )

                batch_datas = variable_pool.get_variable(
                    node_id=self.node_id,
                    key_name=self.input_identifier[0],
                    span=span_context,
                )
                inputs = {self.input_identifier[0]: batch_datas}
                span_context.add_info_events({"inputs": f"{inputs}"})

                batch_result_dict: dict[str, list] = {}
                temp_variable_pool = copy.deepcopy(variable_pool)
                for batch_data in batch_datas:
                    res = await self._process_single_batch(
                        batch_data,
                        temp_variable_pool,
                        source_iteration_chains,
                        span_context,
                        iteration_one_engine,
                        variable_pool,
                        callbacks,
                        event_log_trace,
                    )
                    cur_batch_res = res.outputs
                    for res_k, res_v in cur_batch_res.items():
                        if res_k not in batch_result_dict:
                            batch_result_dict[res_k] = []
                        batch_result_dict[res_k].append(res_v)

                return_result = {}
                for out_put_key_name in self.output_identifier:
                    return_result[out_put_key_name] = batch_result_dict.get(
                        out_put_key_name, []
                    )
                span_context.add_info_events({"ret": f"{return_result}"})
            except CustomException as err:
                span_context.record_exception(err)
                return NodeRunResult(
                    status=WorkflowNodeExecutionStatus.FAILED,
                    inputs=inputs,
                    error=err,
                    node_id=self.node_id,
                    alias_name=self.alias_name,
                    node_type=self.node_type,
                )
            except Exception as err:
                traceback.print_exc()
                span_context.record_exception(err)
                return NodeRunResult(
                    status=WorkflowNodeExecutionStatus.FAILED,
                    inputs=inputs,
                    error=CustomException(
                        CodeEnum.IterationExecutionError,
                        cause_error=err,
                    ),
                    node_id=self.node_id,
                    alias_name=self.alias_name,
                    node_type=self.node_type,
                )
            finally:
                node_run_status[self.node_id].complete.set()

            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=inputs,
                outputs=return_result,
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )

    async def _process_single_batch(
        self,
        batch_data: Any,
        temp_variable_pool: VariablePool,
        source_iteration_chains: Chains,
        span: Span,
        iteration_one_engine: Any,
        variable_pool: VariablePool,
        callbacks: ChatCallBacks,
        event_log_trace: WorkflowLog,
    ) -> NodeRunResult:
        """
        Process a single batch item through the iteration workflow.

        This method sets up a fresh execution environment for each batch item,
        runs the complete iteration workflow, and returns the results.

        :param batch_data: Single item from the batch to be processed
        :param temp_variable_pool: Temporary variable pool for this iteration
        :param source_iteration_chains: Source chains configuration for iteration
        :param span: Tracing span for monitoring and debugging
        :param iteration_one_engine: Workflow engine instance for this iteration
        :param variable_pool: Original variable pool containing history and context
        :param callbacks: Callback handlers for the workflow execution
        :param event_log_trace: Event logging trace for the workflow
        :return: NodeRunResult containing the execution results for this batch item
        """
        cur_batch_data_dict = {self.input_identifier[0]: batch_data}

        # Prepare execution environment for this iteration
        new_variable_pool = copy.deepcopy(temp_variable_pool)
        iteration_chains = copy.deepcopy(source_iteration_chains)

        iteration_one_engine.engine_ctx.variable_pool = new_variable_pool
        iteration_one_engine.engine_ctx.chains = iteration_chains

        # Convert legacy history format for compatibility
        history = []
        history_ai_msg = variable_pool.get_history(node_id=self.node_id)
        for h in history_ai_msg:
            history.append(h.dict())

        # Use original history for iteration nodes without rounds and token processing
        history_v2 = []
        if variable_pool.history_v2:
            history_v2 = variable_pool.history_v2.origin_history
        res = await iteration_one_engine.async_run(
            inputs=cur_batch_data_dict,
            span=span,
            callback=callbacks,
            history=history,
            history_v2=history_v2,
            event_log_trace=event_log_trace,
        )

        # Reset node running status after each iteration execution
        self._init_iteration_node(
            iteration_one_engine.engine_ctx.node_run_status,
            iteration_chains,
            variable_pool,
        )
        iteration_one_engine.engine_ctx.end_complete = asyncio.Event()
        return res

    def _init_iteration_node(
        self,
        node_run_status: Dict[str, NodeRunningStatus],
        chains: Chains,
        variable_pool: VariablePool,
    ) -> None:
        """
        Initialize iteration node running status for the next iteration.

        This method resets all node running status flags and clears stream data
        for message and end nodes to prepare for the next iteration execution.

        :param node_run_status: Dictionary mapping node IDs to their running status
        :param chains: Workflow chains configuration
        :param variable_pool: Variable pool containing stream data to be reset
        """
        try:
            for master_chain in chains.master_chains:
                for node_id in master_chain.node_id_list:
                    node_run_status[node_id].processing.clear()
                    node_run_status[node_id].complete.clear()
                    node_run_status[node_id].start_with_thread.clear()
                    node_run_status[node_id].pre_processing.clear()
                    node_run_status[node_id].not_run.clear()
                    # Reset stream data for message and end nodes within iteration
                    if node_id.split(":")[0] in [
                        NodeType.MESSAGE.value,
                        NodeType.ITERATION_END.value,
                    ]:
                        if node_id not in variable_pool.stream_data:
                            continue
                        for k, _ in variable_pool.stream_data[node_id].items():
                            variable_pool.stream_data[node_id][k] = asyncio.Queue()
        except Exception as e:
            raise e


class IterationStartNode(BaseNode):
    """
    Start node for iteration workflow subgraph.

    This node serves as the entry point for each iteration within an iteration node.
    It retrieves variables from the variable pool and passes them to the next nodes
    in the iteration workflow.
    """

    def get_node_config(self) -> Dict[str, Any]:
        """
        Get the configuration parameters for this iteration start node.

        :return: Empty dictionary as this node has no specific configuration
        """
        return {}

    def sync_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Synchronous execution method (not implemented for iteration start nodes).

        :param variable_pool: Pool of variables for the workflow execution
        :param span: Tracing span for monitoring and debugging
        :param event_log_node_trace: Optional node-level event logging
        :param kwargs: Additional keyword arguments including callback methods
        :return: NodeRunResult containing execution status and outputs
        """
        raise NotImplementedError

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronously execute the iteration start node.

        This method retrieves variables from the variable pool based on the output
        identifiers and returns them as the node's outputs.

        :param variable_pool: Pool of variables for the workflow execution
        :param span: Tracing span for monitoring and debugging
        :param event_log_node_trace: Optional node-level event logging
        :param kwargs: Additional keyword arguments
        :return: NodeRunResult containing execution status and retrieved variables
        """
        outputs: dict = {}  # node outputs
        try:
            for key in self.output_identifier:
                outputs[key] = variable_pool.get_variable(
                    node_id=self.node_id, key_name=key, span=span
                )
            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=outputs,
                outputs={},
                node_id=self.node_id,
                node_type=self.node_type,
                alias_name=self.alias_name,
            )
        except Exception as e:
            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.FAILED,
                error=CustomException(
                    CodeEnum.IterationExecutionError,
                    cause_error=e,
                ),
                inputs=outputs,
                outputs={},
                node_id=self.node_id,
                node_type=self.node_type,
                alias_name=self.alias_name,
            )


class IterationEndNode(BaseNode):
    """
    End node for iteration workflow subgraph.

    This node serves as the exit point for each iteration within an iteration node.
    It processes the final outputs and can apply template transformations based on
    the configured output mode.
    """

    template: str = ""
    outputMode: int

    def get_node_config(self) -> Dict[str, Any]:
        """
        Get the configuration parameters for this iteration end node.

        :return: Dictionary containing template and output mode configuration
        """
        return {"template": self.template, "outputMode": self.outputMode}

    def sync_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Synchronous execution method (not implemented for iteration end nodes).

        :param variable_pool: Pool of variables for the workflow execution
        :param span: Tracing span for monitoring and debugging
        :param event_log_node_trace: Optional node-level event logging
        :param kwargs: Additional keyword arguments
        :return: NodeRunResult containing execution status and outputs
        """
        raise NotImplementedError

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronously execute the iteration end node.

        This method processes the final outputs of the iteration, retrieves variables
        from the variable pool, and optionally applies template transformations based
        on the output mode configuration.

        :param variable_pool: Pool of variables for the workflow execution
        :param span: Tracing span for monitoring and debugging
        :param event_log_node_trace: Optional node-level event logging
        :param kwargs: Additional keyword arguments
        :return: NodeRunResult containing execution status and processed outputs
        """
        prompt_template = self.template
        inputs: dict = {}
        outputs: dict = {}
        try:
            for end_input in self.input_identifier:
                outputs.update(
                    {
                        end_input: variable_pool.get_variable(
                            node_id=self.node_id, key_name=end_input, span=span
                        )
                    }
                )

            if self.outputMode == 1:
                prompt_template = prompt_template_replace(
                    input_identifier=self.input_identifier,
                    _prompt_template=prompt_template,
                    node_id=self.node_id,
                    variable_pool=variable_pool,
                    span_context=span,
                )
                # Apply template variable replacement
                # prompt_template = replace_variables(prompt_template, replacements_str)
                # outputs.update({"res": prompt_template})
            # elif self.outputMode == 0:

            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=inputs,
                outputs=outputs,
                node_id=self.node_id,
                node_answer_content=prompt_template,
                node_type=self.node_type,
                alias_name=self.alias_name,
            )
        except Exception as err:
            span.record_exception(err)
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=CustomException(
                    CodeEnum.EndNodeExecutionError,
                    cause_error=err,
                ),
            )
