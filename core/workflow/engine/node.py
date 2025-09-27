import copy
import json
from abc import ABC, abstractmethod
from typing import Any, Dict, List, Optional, Union, cast

from pydantic import BaseModel
from workflow.engine.callbacks.callback_handler import ChatCallBacks
from workflow.engine.entities.chains import Chains
from workflow.engine.entities.msg_or_end_dep_info import MsgOrEndDepInfo
from workflow.engine.entities.node_entities import NodeType
from workflow.engine.entities.node_running_status import NodeRunningStatus
from workflow.engine.entities.retry_config import RetryConfig
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.entities.workflow_dsl import InputItem, Node, OutputItem
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.cache_node import tool_classes
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.log_trace.workflow_log import WorkflowLog
from workflow.extensions.otlp.trace.span import Span
from workflow.service.history_service import add_history


class NodeParameterStrategy(ABC):
    """Abstract base class for node parameter building strategies."""

    @abstractmethod
    def build_parameters(
        self, base_params: Dict[str, Any], **kwargs: Any
    ) -> Dict[str, Any]:
        """Build node-specific parameters.

        :param base_params: Base parameters dictionary
        :param kwargs: Additional keyword arguments
        :return: Updated parameters dictionary
        """
        pass


class DefaultParameterStrategy(NodeParameterStrategy):
    """Default parameter strategy that returns base parameters unchanged."""

    def build_parameters(
        self, base_params: Dict[str, Any], **kwargs: Any
    ) -> Dict[str, Any]:
        """Build parameters using default strategy.

        :param base_params: Base parameters dictionary
        :param kwargs: Additional keyword arguments (ignored)
        :return: Base parameters unchanged
        """
        return base_params


class MessageNodeParameterStrategy(NodeParameterStrategy):
    """Parameter strategy for message-type nodes."""

    def build_parameters(
        self, base_params: Dict[str, Any], **kwargs: Any
    ) -> Dict[str, Any]:
        """Build parameters for message-type nodes.

        :param base_params: Base parameters dictionary
        :param kwargs: Additional keyword arguments containing message dependencies
        :return: Updated parameters with message-specific data
        """
        base_params.update(
            {
                "msg_or_end_node_deps": kwargs.get("msg_or_end_node_deps", {}),
                "node_run_status": kwargs.get("node_run_status", {}),
                "chains": kwargs.get("chains"),
            }
        )
        return base_params


class IterationNodeParameterStrategy(NodeParameterStrategy):
    """Parameter strategy for iteration nodes."""

    def build_parameters(
        self, base_params: Dict[str, Any], **kwargs: Any
    ) -> Dict[str, Any]:
        """Build parameters for iteration nodes.

        :param base_params: Base parameters dictionary
        :param kwargs: Additional keyword arguments containing iteration data
        :return: Updated parameters with iteration-specific data
        """
        base_params.update(
            {
                "msg_or_end_node_deps": kwargs.get("msg_or_end_node_deps", {}),
                "node_run_status": kwargs.get("node_run_status", {}),
                "chains": kwargs.get("chains"),
                "built_nodes": kwargs.get("built_nodes", {}),
            }
        )
        return base_params


class NodeExecutionTemplate:
    """Template class for node execution using template method pattern."""

    def __init__(self, node: "SparkFlowEngineNode") -> None:
        """Initialize the node execution template.

        :param node: The SparkFlow engine node to execute
        """
        self.node = node
        self.parameter_strategies = self._init_parameter_strategies()

    def _init_parameter_strategies(self) -> Dict[str, NodeParameterStrategy]:
        """Initialize parameter strategy mapping for different node types.

        :return: Dictionary mapping node types to their parameter strategies
        """
        return {
            NodeType.MESSAGE.value: MessageNodeParameterStrategy(),
            NodeType.END.value: MessageNodeParameterStrategy(),
            NodeType.LLM.value: MessageNodeParameterStrategy(),
            NodeType.AGENT.value: MessageNodeParameterStrategy(),
            NodeType.KNOWLEDGE_PRO.value: MessageNodeParameterStrategy(),
            NodeType.FLOW.value: MessageNodeParameterStrategy(),
            NodeType.ITERATION.value: IterationNodeParameterStrategy(),
        }

    async def execute(self, **kwargs: Any) -> NodeRunResult:
        """Execute the node using template method pattern.

        :param kwargs: Execution parameters including variable_pool, span, callbacks, etc.
        :return: Node execution result
        :raises CustomException: When node execution fails
        """
        span = kwargs.get("span", Span())
        with span.start(f"run_node:{self.node.node_id}") as span_context:

            # Set up logging
            self.node.node_log.sid = span_context.sid
            self.node.node_log.set_start()

            # Set next node log IDs
            engine_nodes = (
                self.node.next_nodes + self.node.fail_nodes
                if hasattr(self.node, "fail_nodes")
                else self.node.next_nodes
            )
            for next_engine_node in engine_nodes:
                self.node.node_log.set_next_node_id(next_engine_node.node_log.id)

            span_context.add_info_event(f"async execute node {self.node.id}")

            try:
                parameters = self._build_execution_parameters(span_context, **kwargs)
                # Execute node logic
                span_context.add_info_events({"config": str(self.node.node_instance)})
                result = await self.node.node_instance.async_execute(**parameters)
                self.node.gather_node_event_log(result)

                await self._handle_execution_result(result, span_context, **kwargs)
                return result
            except CustomException:
                raise
            except Exception as err:
                raise CustomException(
                    CodeEnum.NODE_RUN_ERROR, cause_error=f"{err}"
                ) from err

    def _build_execution_parameters(
        self, span_context: Span, **kwargs: Any
    ) -> Dict[str, Any]:
        """Build execution parameters using appropriate strategy.

        :param span_context: Tracing span context
        :param kwargs: Additional execution parameters
        :return: Complete execution parameters dictionary
        """
        base_params = {
            "variable_pool": kwargs.get("variable_pool"),
            "span": span_context,
            "iteration_engine": kwargs.get("iteration_engine"),
            "event_log_node_trace": self.node.node_log,
            "event_log_trace": kwargs.get("event_log_trace"),
            "callbacks": kwargs.get("callbacks"),
            "stream_node_info": self.node.stream_node_info,
        }

        node_type = self.node.node_id.split("::")[0]
        strategy = self.parameter_strategies.get(node_type, DefaultParameterStrategy())
        return strategy.build_parameters(base_params, **kwargs)

    async def _handle_execution_result(
        self, result: NodeRunResult, span_context: Span, **kwargs: Any
    ) -> None:
        """Handle node execution result based on status.

        :param result: Node execution result
        :param span_context: Tracing span context
        :param kwargs: Additional parameters for result handling
        """
        if result.status == WorkflowNodeExecutionStatus.CANCELLED:
            self._handle_cancelled_result(
                result, span_context, cast(WorkflowLog, kwargs.get("event_log_trace"))
            )
            return

        if result.status != WorkflowNodeExecutionStatus.SUCCEEDED:
            self._handle_failed_result(result, span_context)
            return

        await self._handle_successful_result(result, span_context, **kwargs)

    def _handle_cancelled_result(
        self, result: NodeRunResult, span_context: Span, event_log_trace: WorkflowLog
    ) -> None:
        """Handle cancelled execution result.

        :param result: Cancelled node execution result
        :param span_context: Tracing span context
        :param event_log_trace: Workflow event log trace
        """
        if event_log_trace:
            event_log_trace.add_node_log([self.node.node_log])
        self.node.node_log.running_status = False
        span_context.add_info_event(f"node {result.node_id} run cancelled.")

    def _handle_failed_result(self, result: NodeRunResult, span_context: Span) -> None:
        """Handle failed execution result.

        :param result: Failed node execution result
        :param span_context: Tracing span context
        :raises CustomException: When node execution fails
        """

        if not result.error:
            raise CustomException(
                CodeEnum.NODE_RUN_ERROR,
                cause_error=f"node {result.node_id} run failed, not error",
            )

        self.node.node_log.running_status = False
        span_context.add_error_event(
            f"node {result.node_id} run failed, "
            f"err code {result.error.code}, err reason: {result.error}"
        )
        raise result.error

    async def _handle_successful_result(
        self, result: NodeRunResult, span_context: Span, **kwargs: Any
    ) -> None:
        """Handle successful execution result.

        :param result: Successful node execution result
        :param span_context: Tracing span context
        :param kwargs: Additional parameters for result processing
        """
        if result.node_id.split(":")[0] != NodeType.LLM.value:
            self.node.node_log.set_node_first_cost_time(-1)

        event_log_trace = cast(WorkflowLog, kwargs.get("event_log_trace"))
        variable_pool = cast(VariablePool, kwargs.get("variable_pool"))
        callbacks = cast(ChatCallBacks, kwargs.get("callbacks"))

        self._add_chat_history_if_needed(result, event_log_trace, variable_pool)
        self._add_variable_to_pool(result, variable_pool, span_context)
        self._log_success_result(result, span_context)
        await self._handle_node_end_callback(result, callbacks)

        if event_log_trace:
            event_log_trace.add_node_log([self.node.node_log])

    def _add_chat_history_if_needed(
        self,
        result: NodeRunResult,
        event_log_trace: WorkflowLog,
        variable_pool: VariablePool,
    ) -> None:
        """Add chat history if needed for LLM or decision nodes.

        :param result: Node execution result
        :param event_log_trace: Workflow event log trace
        :param variable_pool: Variable pool containing node protocols
        """
        if not (
            result.node_id.split(":")[0] == NodeType.LLM.value
            or result.node_id.split(":")[0] == NodeType.DECISION_MAKING.value
        ):
            return

        enable_chat_history_v1 = variable_pool.get_node_protocol(
            result.node_id
        ).nodeParam.get("enableChatHistory", False)

        if enable_chat_history_v1:
            add_history(
                flow_id=event_log_trace.flow_id,
                node_id=result.node_id,
                uid=event_log_trace.uid,
                raw_question={
                    "role": "user",
                    "content": (
                        result.process_data.get("query", "")
                        if result.process_data
                        else ""
                    ),
                },
                raw_answer={"role": "assistant", "content": result.raw_output},
            )

    def _should_add_chat_history(self, result: NodeRunResult) -> bool:
        """Check if chat history should be added for this node type.

        :param result: Node execution result
        :return: True if chat history should be added
        """
        node_type = result.node_id.split(":")[0]
        return node_type in [NodeType.LLM.value, NodeType.DECISION_MAKING.value]

    def _add_variable_to_pool(
        self, result: NodeRunResult, variable_pool: VariablePool, span_context: Span
    ) -> None:
        """Add execution result to variable pool based on node type.

        :param result: Node execution result
        :param variable_pool: Variable pool to add result to
        :param span_context: Tracing span context
        """
        node_type = result.node_id.split(":")[0]

        if node_type == NodeType.START.value:
            self._add_start_node_variables(result, variable_pool, span_context)
        elif node_type == NodeType.END.value:
            self._add_end_node_variables(result, variable_pool, span_context)
        else:
            self._add_default_node_variables(result, variable_pool, span_context)

    def _add_start_node_variables(
        self, result: NodeRunResult, variable_pool: VariablePool, span_context: Span
    ) -> None:
        """Add start node variables to variable pool.

        :param result: Start node execution result
        :param variable_pool: Variable pool to add variables to
        :param span_context: Tracing span context
        :raises CustomException: When variable addition fails
        """
        res_bak = copy.deepcopy(result)
        res_bak.outputs = res_bak.inputs
        output_keys = list(res_bak.outputs.keys())

        try:
            variable_pool.add_variable(
                result.node_id, output_keys, res_bak, span=span_context
            )
        except Exception as err:
            raise CustomException(
                err_code=CodeEnum.VARIABLE_POOL_SET_PARAMETER_ERROR,
                err_msg=f"Node name: {self.node.node_id}, error message: {err}",
            ) from err

    def _add_end_node_variables(
        self, result: NodeRunResult, variable_pool: VariablePool, span_context: Span
    ) -> None:
        """Add end node variables to variable pool.

        :param result: End node execution result
        :param variable_pool: Variable pool to add variables to
        :param span_context: Tracing span context
        :raises CustomException: When variable addition fails
        """
        res_bak = copy.deepcopy(result)
        res_bak.outputs = {}
        output_keys = list(res_bak.outputs.keys())

        try:
            variable_pool.add_variable(
                result.node_id, output_keys, res_bak, span=span_context
            )
        except Exception as err:
            raise CustomException(
                err_code=CodeEnum.VARIABLE_POOL_SET_PARAMETER_ERROR,
                err_msg=f"Node name: {self.node.node_id}, error message: {err}",
            ) from err

    def _add_default_node_variables(
        self, result: NodeRunResult, variable_pool: VariablePool, span_context: Span
    ) -> None:
        """Add default node variables to variable pool.

        :param result: Node execution result
        :param variable_pool: Variable pool to add variables to
        :param span_context: Tracing span context
        :raises CustomException: When variable addition fails
        """
        output_keys = list(result.outputs.keys())

        try:
            variable_pool.add_variable(
                result.node_id, output_keys, result, span=span_context
            )
        except Exception as err:
            raise CustomException(
                err_code=CodeEnum.VARIABLE_POOL_SET_PARAMETER_ERROR,
                err_msg=f"Node name: {self.node.node_id}, error message: {err}",
            ) from err

    def _log_success_result(self, result: NodeRunResult, span_context: Span) -> None:
        """Log successful execution result.

        :param result: Successful node execution result
        :param span_context: Tracing span context
        """
        res_wb = result.dict()
        res_wb.update({"status": "succeed"})
        span_context.add_info_events(
            {"node_result": json.dumps(res_wb, ensure_ascii=False)}
        )

    async def _handle_node_end_callback(
        self, result: NodeRunResult, callbacks: Optional[ChatCallBacks]
    ) -> None:
        """Handle node end callback for non-message and non-end nodes.

        :param result: Node execution result
        :param callbacks: Chat callbacks handler
        """
        if not callbacks:
            return

        # Message and end nodes control their own start and end frames
        if self.node.node_id.split("::")[0] not in [
            NodeType.MESSAGE.value,
            NodeType.END.value,
        ]:
            await callbacks.on_node_end(
                node_id=self.node.node_id,
                alias_name=self.node.node_alias_name,
                message=result,
            )


class SparkFlowEngineNode(BaseModel):
    """
    Spark Flow Engine Node class.

    Manages individual nodes in a workflow, including node execution, logging,
    and relationship management. Uses strategy pattern and template method pattern
    to reduce complexity and improve maintainability.
    """

    class Config:
        arbitrary_types_allowed = True

    # Node basic information
    node_id: str  # Unique node identifier
    node_type: str  # Node type
    node_alias_name: str  # Node alias name
    node_instance: BaseNode  # Node instance

    # Classification node specific configuration
    node_classes: Dict[str, List[str]] = {}  # Mapping from intent ID to node ID

    # Streaming node information
    stream_node_info: Dict[str, Any] = {}

    # Node relationships
    next_nodes: List["SparkFlowEngineNode"] = []  # List of next nodes
    next_nodes_count: int = 0  # Count of next nodes
    fail_nodes: List["SparkFlowEngineNode"] = []  # List of failure handling nodes
    fail_nodes_count: int = 0  # Count of failure nodes
    pre_nodes: List["SparkFlowEngineNode"] = []  # List of previous nodes
    pre_nodes_count: int = 0  # Count of previous nodes

    # Logging related
    node_log: NodeLog  # Node logger

    # LLM related node types list
    llm_nodes: List[str] = [
        NodeType.DECISION_MAKING.value,
        NodeType.LLM.value,
        NodeType.PARAMETER_EXTRACTOR.value,
    ]

    def __init__(self, **kwargs: Any) -> None:
        """Initialize the SparkFlow engine node.

        :param kwargs: Node initialization parameters including node_id, node_type, etc.
        """
        node_log_tmp = NodeLog(
            node_id=kwargs.get("node_id"),
            node_name=kwargs.get("node_alias_name"),
            node_type=kwargs.get("node_type"),
            sid="",
        )
        super().__init__(node_log=node_log_tmp, **kwargs)

    @property
    def id(self) -> str:
        """Get node ID.

        :return: Node identifier
        """
        return self.node_id

    def add_classify_class(self, source_handle: str, target_node_id: str) -> None:
        """
        Add classification mapping for classification nodes.

        :param source_handle: Source handle (intent ID)
        :param target_node_id: Target node ID
        """
        if source_handle not in self.node_classes:
            self.node_classes[source_handle] = []
        self.node_classes[source_handle].append(target_node_id)

    def get_classify_class(self) -> Dict[str, List[str]]:
        """Get classification class mapping.

        :return: Dictionary mapping intent IDs to node IDs
        """
        return self.node_classes

    def add_pre_node(self, node: "SparkFlowEngineNode") -> None:
        """Add a previous node.

        :param node: Previous node to add
        """
        self.pre_nodes.append(node)
        self.pre_nodes_count += 1

    def add_next_node(self, node: "SparkFlowEngineNode") -> None:
        """Add a next node.

        :param node: Next node to add
        """
        self.next_nodes.append(node)
        self.next_nodes_count += 1

    def add_fail_node(self, node: "SparkFlowEngineNode") -> None:
        """Add a failure handling node.

        :param node: Failure handling node to add
        """
        self.fail_nodes.append(node)
        self.fail_nodes_count += 1

    def get_next_nodes(self) -> List["SparkFlowEngineNode"]:
        """Get list of next nodes.

        :return: List of next nodes
        """
        return self.next_nodes

    def get_fail_nodes(self) -> List["SparkFlowEngineNode"]:
        """Get list of failure handling nodes.

        :return: List of failure handling nodes
        """
        return self.fail_nodes if hasattr(self, "fail_nodes") else []

    def get_pre_nodes(self) -> List["SparkFlowEngineNode"]:
        """Get list of previous nodes.

        :return: List of previous nodes
        """
        return self.pre_nodes

    def gather_node_event_log(self, result: NodeRunResult) -> None:
        """
        Collect and record node event logs.

        :param result: Node execution result
        """
        # Process input data
        for key, value in result.inputs.items():
            if isinstance(value, (list, dict)):
                value = json.dumps(value, ensure_ascii=False)
            else:
                value = str(value)
            self.node_log.append_input_data(key, value)

        # Process output data
        for key, value in result.outputs.items():
            if isinstance(value, (list, dict)):
                value = json.dumps(value, ensure_ascii=False)
            else:
                value = str(value)
            self.node_log.append_output_data(key, value)

        # Record token consumption
        if result.token_cost:
            self.node_log.append_usage_data(result.token_cost.dict())

        # Record LLM output
        if self.node_id.split(":")[0] in self.llm_nodes:
            self.node_log.llm_output = result.raw_output
        self.node_log.set_end()

    async def async_call(
        self,
        variable_pool: Union[VariablePool, List[VariablePool]],
        span: Span,
        callbacks: ChatCallBacks,
        iteration_engine: Any,
        event_log_trace: WorkflowLog,
        msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo],
        node_run_status: Dict[str, NodeRunningStatus],
        chains: Chains,
        built_nodes: Dict[str, Any],
    ) -> NodeRunResult:
        """
        Asynchronously execute the node.

        :param variable_pool: Variable pool
        :param span: Tracing span
        :param callbacks: Callback handler
        :param iteration_engine: Iteration engine
        :param event_log_trace: Event log trace
        :param msg_or_end_node_deps: Message or end node dependencies
        :param node_run_status: Node running status
        :param chains: Execution chains
        :param built_nodes: Built nodes
        :return: Node execution result
        """
        # Use template method pattern to execute node
        executor = NodeExecutionTemplate(self)
        return await executor.execute(
            variable_pool=variable_pool,
            span=span,
            callbacks=callbacks,
            iteration_engine=iteration_engine,
            event_log_trace=event_log_trace,
            msg_or_end_node_deps=msg_or_end_node_deps or {},
            node_run_status=node_run_status or {},
            chains=chains,
            built_nodes=built_nodes or {},
        )


class NodeFactory:
    """Factory class for creating SparkFlow engine nodes."""

    @staticmethod
    def create(node: Node, span_context: Span) -> SparkFlowEngineNode:
        """
        Create a node instance.

        :param node: Node data
        :param span_context: Span context for tracing
        :return: Created node instance
        :raises CustomException: When node type is not supported
        """
        node_class = tool_classes.get(node.get_node_type())
        if not node_class:
            raise CustomException(
                CodeEnum.ENG_NODE_PROTOCOL_VALIDATE_ERROR,
                err_msg=f"Current workflow does not support node type: {node.get_node_type()}",
                cause_error=f"Current workflow does not support node type: {node.get_node_type()}",
            )

        if not node.data:
            return node_class(span=span_context)

        # Get basic configuration
        inputs = node.data.inputs
        outputs = node.data.outputs

        # Build retry configuration
        retry_config = node.data.retryConfig
        retry_config.custom_output = NodeFactory._check_custom_output(
            retry_config.custom_output, outputs, span_context
        )

        # Create instance based on node type
        if node.get_node_type() == NodeType.QUESTION_ANSWER.value:
            node_instance = NodeFactory._create_question_answer_node(
                node_class, inputs, outputs, retry_config, node, span_context
            )
        elif node.get_node_type() == NodeType.PARAMETER_EXTRACTOR.value:
            node_instance = NodeFactory._create_parameter_extractor_node(
                node_class, inputs, outputs, retry_config, node, span_context
            )
        else:
            node_instance = NodeFactory._create_default_node(
                node_class,
                inputs,
                outputs,
                retry_config,
                node,
                span_context,
            )
        return SparkFlowEngineNode(
            node_id=node.id,
            node_type=node.data.nodeMeta.nodeType,
            node_alias_name=node.data.nodeMeta.aliasName,
            node_instance=node_instance,
        )

    @staticmethod
    def _check_custom_output(
        custom_output: dict, outputs: List[OutputItem], span_context: Span
    ) -> dict:
        """Build retry configuration for the node.

        :param retry_config_data: Retry configuration data
        :param outputs: Node output definitions
        :param span_context: Span context for tracing
        :return: Retry configuration object
        :raises CustomException: When custom output validation fails
        """
        if custom_output:
            if not NodeFactory._validate_custom_output(custom_output, outputs):
                span_context.add_error_event(
                    f"custom_output {custom_output} not formatted"
                )
                raise CustomException(
                    CodeEnum.ENG_NODE_PROTOCOL_VALIDATE_ERROR,
                    err_msg=f"Node set output content: {custom_output} does not match format",
                )
        else:
            custom_output = NodeFactory._create_default_output(outputs)
        return custom_output

    @staticmethod
    def _validate_custom_output(
        custom_output: Dict[str, Any], outputs: List[OutputItem]
    ) -> bool:
        """Validate custom output format against output schema.

        :param custom_output: Custom output data to validate
        :param outputs: Output schema definitions
        :return: True if validation passes, False otherwise
        """
        declared = {o.name: o.output_schema for o in outputs}

        # Check for extra fields
        if any(k not in declared for k in custom_output):
            return False

        # Check for missing required fields
        for name, schema in declared.items():
            if name not in custom_output and "default" not in schema:
                return False

        # Check type matching
        for name, schema in declared.items():
            if name not in custom_output:
                continue

            value = custom_output[name]
            expected = schema["type"]

            if not NodeFactory._check_type_match(value, expected, schema):
                return False

        return True

    @staticmethod
    def _check_type_match(value: Any, expected_type: str, schema: Dict) -> bool:
        """Check if value type matches expected type according to schema.

        :param value: Value to check
        :param expected_type: Expected type string
        :param schema: Schema definition
        :return: True if type matches, False otherwise
        """
        type_checkers = {
            "string": lambda v: isinstance(v, str),
            "boolean": lambda v: isinstance(v, bool),
            "integer": lambda v: isinstance(v, int) and not isinstance(v, bool),
            "number": lambda v: isinstance(v, (int, float)) and not isinstance(v, bool),
            "array": lambda v: isinstance(v, list),
            "object": lambda v: isinstance(v, dict),
        }

        checker = type_checkers.get(expected_type)
        if not checker:
            return False

        if not checker(value):
            return False

        # Special handling for array type element checking
        if expected_type == "array" and "items" in schema:
            items_schema = schema["items"]
            if "type" in items_schema:
                item_type = items_schema["type"]
                item_checker = type_checkers.get(item_type)
                if item_checker and not all(item_checker(item) for item in value):
                    return False

        return True

    @staticmethod
    def _create_default_output(outputs: List[OutputItem]) -> Dict[str, Any]:
        """Create default output values based on output schema.

        :param outputs: Output schema definitions
        :return: Dictionary with default output values
        """
        custom_output: Dict[str, Any] = {}
        for output_decl in outputs:
            name = output_decl.name
            schema = output_decl.output_schema
            if "default" in schema:
                custom_output[name] = schema["default"]
            else:
                type_str = schema["type"]
                if type_str == "string":
                    custom_output[name] = ""
                elif type_str == "boolean":
                    custom_output[name] = False
                elif type_str == "integer":
                    custom_output[name] = 0
                elif type_str == "number":
                    custom_output[name] = 0.0
                elif type_str == "array":
                    custom_output[name] = []
                elif type_str == "object":
                    custom_output[name] = {}
                else:
                    custom_output[name] = None  # Fallback for unknown types
        return custom_output

    @staticmethod
    def _create_question_answer_node(
        node_class: Any,
        inputs: List[InputItem],
        outputs: List[OutputItem],
        retry_config: RetryConfig,
        node: Node,
        span_context: Span,
    ) -> Any:
        """Create question-answer node instance.

        :param node_class: Node class to instantiate
        :param inputs: Input definitions
        :param outputs: Output definitions
        :param retry_config: Retry configuration
        :param node: Node data
        :param span_context: Span context for tracing
        :return: Created question-answer node instance
        """
        input_keys = [node_input.name for node_input in inputs]
        output_keys = [node_output.name for node_output in outputs]

        extra_params: list[OutputItem] = []
        default_outputs = {}
        # Get slot extraction values
        SYSTEM_VARIABLE = ["query", "content"]

        for node_output in outputs:
            if node_output.name not in SYSTEM_VARIABLE:
                extra_params.append(node_output)
        # Get output default values
        for default_output in outputs:
            _output = default_output.name
            if _output not in SYSTEM_VARIABLE:
                _default_value = default_output.output_schema.get("default", "")
                default_outputs[_output] = _default_value
        return node_class(
            node_id=node.id,
            alias_name=node.data.nodeMeta.aliasName,
            node_type=node.data.nodeMeta.nodeType,
            input_identifier=input_keys,
            output_identifier=output_keys,
            retry_config=retry_config,
            span=span_context,
            extractor_params=extra_params,
            default_outputs=default_outputs,
            **node.data.nodeParam,
        )

    @staticmethod
    def _create_parameter_extractor_node(
        node_class: Any,
        inputs: List[InputItem],
        outputs: List[OutputItem],
        retry_config: RetryConfig,
        node: Node,
        span_context: Span,
    ) -> Any:
        """Create parameter extractor node instance.

        :param node_class: Node class to instantiate
        :param inputs: Input definitions
        :param outputs: Output definitions
        :param retry_config: Retry configuration
        :param node: Node data
        :param span_context: Span context for tracing
        :return: Created parameter extractor node instance
        """
        input_keys = [node_input.name for node_input in inputs]
        output_keys = [node_output.name for node_output in outputs]
        extra_params: list[OutputItem] = []
        for node_output in outputs:
            extra_params.append(node_output)
        return node_class(
            node_id=node.id,
            alias_name=node.data.nodeMeta.aliasName,
            node_type=node.data.nodeMeta.nodeType,
            input_identifier=input_keys,
            output_identifier=output_keys,
            retry_config=retry_config,
            span=span_context,
            extractor_params=extra_params,
            **node.data.nodeParam,
        )

    @staticmethod
    def _create_default_node(
        node_class: Any,
        inputs: List[InputItem],
        outputs: List[OutputItem],
        retry_config: RetryConfig,
        node: Node,
        span_context: Span,
    ) -> Any:
        """Create default node instance.

        :param node_class: Node class to instantiate
        :param inputs: Input definitions
        :param outputs: Output definitions
        :param retry_config: Retry configuration
        :param node: Node data
        :param span_context: Span context for tracing
        :return: Created default node instance
        """
        input_keys: list[Any] = []
        output_keys = [node_output.name for node_output in outputs]

        # Special handling for if-else nodes
        if node.get_node_type() == NodeType.IF_ELSE.value:
            id_name_dict = {}
            for node_input in inputs:
                id_name_dict[node_input.id] = node_input.name
            input_keys.append(id_name_dict)
        else:
            input_keys = [node_input.name for node_input in inputs]

        for node_output in outputs:
            output_keys.append(node_output.name)

        return node_class(
            node_id=node.id,
            alias_name=node.data.nodeMeta.aliasName,
            node_type=node.data.nodeMeta.nodeType,
            input_identifier=input_keys,
            output_identifier=output_keys,
            retry_config=retry_config,
            span=span_context,
            **node.data.nodeParam,
        )
