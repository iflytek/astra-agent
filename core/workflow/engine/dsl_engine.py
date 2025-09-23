"""
DSL Engine Module

This module provides the core workflow execution engine that processes workflow DSL (Domain Specific Language)
and executes nodes in a distributed, asynchronous manner. It includes error handling, retry mechanisms,
and various execution strategies for different node types.
"""

import asyncio
import pickle
import time
from abc import ABC, abstractmethod
from asyncio.tasks import Task
from typing import Any, Dict, List, Optional, Tuple

from pydantic import BaseModel, Field

from workflow.consts.flow import ErrorHandler, XFLLMStatus
from workflow.consts.model_provider import ModelProviderEnum
from workflow.domain.entities.chat import HistoryItem
from workflow.engine.callbacks.callback_handler import ChatCallBacks
from workflow.engine.entities.chains import Chains, SimplePath
from workflow.engine.entities.msg_or_end_dep_info import MsgOrEndDepInfo
from workflow.engine.entities.node_entities import (
    CONTINUE_ON_ERROR_NOT_STREAM_NODE_TYPE,
    CONTINUE_ON_ERROR_STREAM_NODE_TYPE,
    NodeType,
)
from workflow.engine.entities.node_running_status import NodeRunningStatus
from workflow.engine.entities.output_mode import EndNodeOutputModeEnum
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.entities.workflow_dsl import Node, WorkflowDSL, Edge
from workflow.engine.node import NodeFactory, SparkFlowEngineNode
from workflow.engine.nodes.base_node import BaseNode, RetryConfig
from workflow.engine.nodes.cache_node import tool_classes
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.exception.e import CustomException, CustomExceptionInterrupt
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.workflow_log import WorkflowLog
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.providers.llm.iflytek_spark.schemas import StreamOutputMsg


async def wait_at_least_one_task_completed(tasks: list[Task]) -> None:
    """
    Wait for at least one task to complete and cancel all pending tasks.

    :param tasks: List of asyncio tasks to wait for
    :return: None
    """
    _, pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
    for task in pending:
        task.cancel()


class WorkflowEngineCtx(BaseModel):
    """
    Workflow engine execution context.

    Contains all necessary state and configuration for workflow execution,
    including variable pool, node status, dependencies, and execution chains.
    """

    # Variable pool for storing and passing variables during execution
    variable_pool: VariablePool

    # Iteration engine instances for supporting loop or recursive execution
    iteration_engine: Dict[str, "WorkflowEngine"] = Field(default_factory=dict)

    # Message and end node dependency information (key: node_id, value: MsgOrEndDepInfo)
    msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo] = Field(default_factory=dict)

    # Node running status (key: node_id, value: NodeRunningStatus)
    node_run_status: Dict[str, NodeRunningStatus] = Field(default_factory=dict)

    # Built node cache (key: node_id, value: SparkFlowEngineNode)
    built_nodes: Dict[str, SparkFlowEngineNode] = Field(default_factory=dict)

    # Execution chains between nodes
    chains: Chains

    # Timestamp when the engine instance was built
    build_timestamp: int = int(time.time())

    # Callback handler for workflow events
    callback: ChatCallBacks = None  # type: ignore
    # Event log trace for workflow execution tracking
    event_log_trace: WorkflowLog = None  # type: ignore

    # Lock for question-answer nodes to ensure serial execution
    qa_node_lock: asyncio.Lock = None  # type: ignore
    # Event to signal workflow completion
    end_complete: asyncio.Event = None  # type: ignore

    # List of node execution results
    responses: list[NodeRunResult] = Field(default_factory=list)
    # List of depth-first search execution tasks
    dfs_tasks: list[Task] = Field(default_factory=list)

    class Config:
        arbitrary_types_allowed = True


class ExceptionHandlerBase(ABC):
    """
    Abstract base class for exception handlers.

    Implements the Chain of Responsibility pattern for handling different types
    of exceptions during workflow execution.
    """

    def __init__(self) -> None:
        self.next_handler: Optional["ExceptionHandlerBase"] = None

    def set_next(self, handler: "ExceptionHandlerBase") -> "ExceptionHandlerBase":
        """
        Set the next handler in the chain.

        :param handler: The next exception handler in the chain
        :return: The handler that was set (for method chaining)
        """
        self.next_handler = handler
        return handler

    @abstractmethod
    async def handle(
        self,
        error: Exception,
        node: SparkFlowEngineNode,
        workflow_engine_ctx: WorkflowEngineCtx,
        attempt: int,
        span: Span,
    ) -> Tuple[Optional[NodeRunResult], bool]:
        """
        Handle an exception that occurred during node execution.

        :param error: The exception that occurred
        :param node: The node where the error occurred
        :param workflow_engine_ctx: The workflow execution context
        :param attempt: Current attempt number (0-based)
        :param span: Tracing span for observability
        :return: Tuple of (handling result, whether to continue processing)
        """
        pass


class TimeoutErrorHandler(ExceptionHandlerBase):
    """
    Handler for timeout errors during node execution.

    Specifically handles asyncio.TimeoutError exceptions and prevents
    further processing in the chain.
    """

    async def handle(
        self,
        error: Exception,
        node: SparkFlowEngineNode,
        workflow_engine_ctx: WorkflowEngineCtx,
        attempt: int,
        span: Span,
    ) -> Tuple[Optional[NodeRunResult], bool]:
        """
        Handle timeout errors.

        :param error: The exception that occurred
        :param node: The node where the error occurred
        :param workflow_engine_ctx: The workflow execution context
        :param attempt: Current attempt number
        :param span: Tracing span for observability
        :return: Tuple of (None, False) for timeout errors, or delegate to next handler
        """
        if isinstance(error, asyncio.TimeoutError):
            return None, False  # Do not continue processing, raise directly

        if self.next_handler:
            return await self.next_handler.handle(
                error, node, workflow_engine_ctx, attempt, span
            )

        return None, False


class CustomExceptionInterruptHandler(ExceptionHandlerBase):
    """
    Handler for custom exception interrupts.

    Handles CustomExceptionInterrupt exceptions by logging the error,
    updating node status, and triggering appropriate callbacks.
    """

    async def handle(
        self,
        error: Exception,
        node: SparkFlowEngineNode,
        workflow_engine_ctx: WorkflowEngineCtx,
        attempt: int,
        span: Span,
    ) -> Tuple[Optional[NodeRunResult], bool]:
        """
        Handle custom exception interrupts.

        :param error: The exception that occurred
        :param node: The node where the error occurred
        :param workflow_engine_ctx: The workflow execution context
        :param attempt: Current attempt number
        :param span: Tracing span for observability
        :return: Tuple of (None, False) for interrupt exceptions, or delegate to next handler
        """
        if isinstance(error, CustomExceptionInterrupt):
            # Log error and raise exception
            event_log_trace = workflow_engine_ctx.event_log_trace

            span.add_error_event(str(error))
            span.record_exception(error)
            event_log_trace.add_node_log([node.node_log])
            node.node_log.running_status = False
            node.node_log.add_error_log(error.message)
            node.node_log.set_end()

            await workflow_engine_ctx.callback.on_node_end(
                node_id=node.node_id,
                alias_name=node.node_alias_name,
                error=error,
            )

            return None, False  # Do not continue processing

        if self.next_handler:
            return await self.next_handler.handle(
                error, node, workflow_engine_ctx, attempt, span
            )

        return None, False


class RetryableErrorHandler(ExceptionHandlerBase):
    """
    Handler for retryable errors during node execution.

    Handles CustomException instances with retry logic, including checking
    for first token sent status and applying different error strategies.
    """

    async def handle(
        self,
        error: Exception,
        node: SparkFlowEngineNode,
        workflow_engine_ctx: WorkflowEngineCtx,
        attempt: int,
        span: Span,
    ) -> Tuple[Optional[NodeRunResult], bool]:
        """
        Handle retryable errors.

        :param error: The exception that occurred
        :param node: The node where the error occurred
        :param workflow_engine_ctx: The workflow execution context
        :param attempt: Current attempt number
        :param span: Tracing span for observability
        :return: Tuple of (result, should_retry) for retryable errors, or delegate to next handler
        """
        if isinstance(error, CustomException):
            retry_config = node.node_instance.retry_config
            max_retries = retry_config.max_retries

            # Check if first token has been sent
            has_sent_first_token = (
                workflow_engine_ctx.variable_pool.get_stream_node_has_sent_first_token(
                    node.node_id
                )
            )

            if has_sent_first_token:
                # If first token was sent, interrupt immediately without fallback logic
                return await self._handle_interruption(
                    error, node, workflow_engine_ctx, span
                )

            if attempt >= max_retries:
                # Maximum retries reached, handle according to error strategy
                return await self._handle_final_retry(
                    error, node, workflow_engine_ctx, span
                )

            # Can continue retrying
            return None, True

        if self.next_handler:
            return await self.next_handler.handle(
                error, node, workflow_engine_ctx, attempt, span
            )

        return None, False

    async def _handle_interruption(
        self,
        error: CustomException,
        node: SparkFlowEngineNode,
        workflow_engine_ctx: WorkflowEngineCtx,
        span: Span,
    ) -> Tuple[Optional[NodeRunResult], bool]:
        """
        Handle interruption scenario.

        :param error: The custom exception that occurred
        :param node: The node where the error occurred
        :param workflow_engine_ctx: The workflow execution context
        :param span: Tracing span for observability
        :return: Tuple of (None, False) indicating no result and no retry
        """

        span.add_error_event(str(error))
        span.record_exception(error)
        workflow_engine_ctx.event_log_trace.add_node_log([node.node_log])
        node.node_log.running_status = False
        node.node_log.add_error_log(error.message)
        node.node_log.set_end()

        await workflow_engine_ctx.callback.on_node_end(
            node_id=node.node_id,
            alias_name=node.node_alias_name,
            error=error,
        )

        return None, False

    async def _handle_final_retry(
        self,
        error: CustomException,
        node: SparkFlowEngineNode,
        workflow_engine_ctx: WorkflowEngineCtx,
        span: Span,
    ) -> Tuple[Optional[NodeRunResult], bool]:
        """
        Handle final retry failure scenario.

        :param error: The custom exception that occurred
        :param node: The node where the error occurred
        :param workflow_engine_ctx: The workflow execution context
        :param span: Tracing span for observability
        :return: Tuple of (result, fail_branch) based on error strategy
        """
        retry_config = node.node_instance.retry_config
        error_strategy = retry_config.error_strategy

        if error_strategy == ErrorHandler.CustomReturn.value:
            # Return custom content
            return await self._create_custom_return_result(
                node, workflow_engine_ctx, error, retry_config.custom_output, span
            )
        elif error_strategy == ErrorHandler.FailBranch.value:
            # Execute failure branch
            return await self._create_fail_branch_result(
                node, workflow_engine_ctx, error, span
            )
        else:
            # Interrupt execution
            return await self._handle_interruption(
                error, node, workflow_engine_ctx, span
            )

    async def _create_custom_return_result(
        self,
        node: SparkFlowEngineNode,
        workflow_engine_ctx: WorkflowEngineCtx,
        error: CustomException,
        custom_output: Dict[str, Any],
        span: Span,
    ) -> Tuple[NodeRunResult, bool]:
        """
        Create custom return result for error handling.

        :param node: The node where the error occurred
        :param workflow_engine_ctx: The workflow execution context
        :param error: The custom exception that occurred
        :param custom_output: Custom output to return
        :param span: Tracing span for observability
        :return: Tuple of (NodeRunResult, False) indicating success with custom output
        """

        # Build input dictionary
        input_dict = {}
        for input_key in node.node_instance.input_identifier:
            input_value = workflow_engine_ctx.variable_pool.get_variable(
                node_id=node.node_id, key_name=input_key, span=span
            )
            input_dict.update({input_key: input_value})

        # Create result
        run_result = NodeRunResult(
            status=WorkflowNodeExecutionStatus.SUCCEEDED,
            inputs=input_dict,
            outputs=custom_output,
            error_outputs={
                "errorCode": error.code,
                "errorMessage": error.message,
            },
            node_id=node.node_id,
            alias_name=node.node_alias_name,
            node_type=node.node_type,
        )

        # Update variable pool
        output_json = {**run_result.outputs, **run_result.error_outputs}
        output_keys = list(output_json.keys())

        try:
            workflow_engine_ctx.variable_pool.add_variable(
                run_result.node_id,
                output_keys,
                run_result,
                span=span,
            )
        except Exception as err:
            raise CustomException(
                err_code=CodeEnum.VariablePoolSetParameterError,
                err_msg=f"Node name: {node.node_id}, error message: {err}",
                cause_error=f"Node name: {node.node_id}, error message: {err}",
            ) from err

        # Handle special logic for streaming nodes
        await self._handle_stream_node_error_output(
            node, workflow_engine_ctx, run_result
        )

        # Callback for node end
        await workflow_engine_ctx.callback.on_node_end(
            node_id=node.node_id,
            alias_name=node.node_alias_name,
            message=run_result,
        )

        return run_result, False

    async def _create_fail_branch_result(
        self,
        node: SparkFlowEngineNode,
        workflow_engine_ctx: WorkflowEngineCtx,
        error: CustomException,
        span: Span,
    ) -> Tuple[NodeRunResult, bool]:
        """
        Create failure branch result.

        :param node: The node where the error occurred
        :param workflow_engine_ctx: The workflow execution context
        :param error: The custom exception that occurred
        :param span: Tracing span for observability
        :return: Tuple of (NodeRunResult, False) indicating failure branch result
        """
        return await self._create_custom_return_result(
            node, workflow_engine_ctx, error, {}, span
        )

    async def _handle_stream_node_error_output(
        self,
        node: SparkFlowEngineNode,
        workflow_engine_ctx: WorkflowEngineCtx,
        run_result: NodeRunResult,
    ) -> None:
        """
        Handle error output for streaming nodes.

        :param node: The node where the error occurred
        :param workflow_engine_ctx: The workflow execution context
        :param run_result: The node run result
        :return: None
        """
        node_type = node.node_id.split("::")[0]
        if node_type not in CONTINUE_ON_ERROR_STREAM_NODE_TYPE:
            return

        # Send error message for each streaming data key
        for key in workflow_engine_ctx.variable_pool.stream_data:
            for stream_node_id in workflow_engine_ctx.variable_pool.stream_data[key]:
                if stream_node_id == run_result.node_id:
                    llm_content = self._get_error_llm_content(node_type, node)

                    await workflow_engine_ctx.variable_pool.stream_data[key][
                        run_result.node_id
                    ].put(
                        StreamOutputMsg(
                            domain=node.node_instance.get_node_config().get(
                                "domain", ""
                            ),
                            llm_response=llm_content,
                            exception_occurred=True,
                        )
                    )

    def _get_error_llm_content(
        self, node_type: str, node: SparkFlowEngineNode
    ) -> Dict[str, Any]:
        """
        Get LLM content for error scenarios.

        :param node_type: The type of the node
        :param node: The node instance
        :return: Dictionary containing error LLM content
        """
        if node_type == NodeType.AGENT.value:
            return {
                "code": -1,
                "choices": [{"finish_reason": "stop"}],
            }
        elif node_type == NodeType.KNOWLEDGE_PRO.value:
            return {
                "code": -1,
                "finish_reason": "stop",
            }
        elif node_type == NodeType.FLOW.value:
            return {
                "code": -1,
                "choices": [{"finish_reason": "stop"}],
            }
        elif node_type == NodeType.LLM.value:
            model_source = node.node_instance.get_node_config().get(
                "source", ModelProviderEnum.XINGHUO.value
            )

            if model_source == ModelProviderEnum.XINGHUO.value:
                return {
                    "header": {
                        "code": -1,
                        "status": XFLLMStatus.END.value,
                    },
                    "payload": {"choices": {"text": [{}]}},
                }
            elif model_source == ModelProviderEnum.OPENAI.value:
                return {
                    "code": -1,
                    "choices": [{"finish_reason": "stop"}],
                }

        return {"code": -1}


class GeneralErrorHandler(ExceptionHandlerBase):
    """
    General error handler for unhandled exceptions.

    Handles any exceptions that are not caught by more specific handlers,
    logging the error and creating appropriate custom exceptions.
    """

    async def handle(
        self,
        error: Exception,
        node: SparkFlowEngineNode,
        workflow_engine_ctx: WorkflowEngineCtx,
        attempt: int,
        span: Span,
    ) -> Tuple[Optional[NodeRunResult], bool]:
        """
        Handle general errors.

        :param error: The exception that occurred
        :param node: The node where the error occurred
        :param workflow_engine_ctx: The workflow execution context
        :param attempt: Current attempt number
        :param span: Tracing span for observability
        :return: Tuple of (None, False) indicating no result and no retry
        """

        span.add_error_event(f"{error}")
        node.node_log.add_error_log(f"{error}")
        workflow_engine_ctx.event_log_trace.add_node_log([node.node_log])
        node.node_log.running_status = False
        node.node_log.set_end()

        custom_error = CustomException(
            CodeEnum.NodeRunErr, err_msg=f"{error}", cause_error=f"{error}"
        )

        await workflow_engine_ctx.callback.on_node_end(
            node_id=node.node_id, alias_name=node.node_alias_name, error=custom_error
        )

        return None, False


class ErrorHandlerChain:
    """
    Error handling chain using Chain of Responsibility pattern.

    Manages a chain of error handlers that process exceptions in sequence,
    with each handler having the opportunity to handle specific error types.
    """

    def __init__(self) -> None:
        self.chain = self._build_chain()

    def _build_chain(self) -> ExceptionHandlerBase:
        """
        Build the error handling chain.

        :return: The first handler in the chain
        """
        timeout_handler = TimeoutErrorHandler()
        interrupt_handler = CustomExceptionInterruptHandler()
        retry_handler = RetryableErrorHandler()
        general_handler = GeneralErrorHandler()

        # Build the responsibility chain
        timeout_handler.set_next(interrupt_handler).set_next(retry_handler).set_next(
            general_handler
        )

        return timeout_handler

    async def handle_error(
        self,
        error: Exception,
        node: SparkFlowEngineNode,
        workflow_engine_ctx: WorkflowEngineCtx,
        attempt: int,
        span: Span,
    ) -> Tuple[Optional[NodeRunResult], bool]:
        """
        Handle an error using the error handling chain.

        :param error: The exception that occurred
        :param node: The node where the error occurred
        :param workflow_engine_ctx: The workflow execution context
        :param attempt: Current attempt number
        :param span: Tracing span for observability
        :return: Tuple of (handling result, whether to continue retrying)
        """
        return await self.chain.handle(error, node, workflow_engine_ctx, attempt, span)


class NodeExecutionStrategy(ABC):
    """
    Abstract base class for node execution strategies.

    Defines the interface for different execution strategies that can be
    applied to different types of nodes during workflow execution.
    """

    @abstractmethod
    async def execute_node(
        self,
        node: SparkFlowEngineNode,
        engine_ctx: WorkflowEngineCtx,
        span: Span,
    ) -> NodeRunResult:
        """
        Execute a node using this strategy.

        :param node: The node to execute
        :param engine_ctx: The execution context
        :param span: Tracing span for observability
        :return: NodeRunResult containing the execution result
        """
        pass

    @abstractmethod
    def can_handle(self, node_type: str) -> bool:
        """
        Check if this strategy can handle the given node type.

        :param node_type: The type of the node
        :return: True if this strategy can handle the node type, False otherwise
        """
        pass


class DefaultNodeExecutionStrategy(NodeExecutionStrategy):
    """
    Default node execution strategy.

    Provides standard execution logic for most node types, setting the
    processing status and calling the node's async_call method.
    """

    async def execute_node(
        self,
        node: SparkFlowEngineNode,
        engine_ctx: WorkflowEngineCtx,
        span: Span,
    ) -> NodeRunResult:
        """
        Execute node using default logic.

        :param node: The node to execute
        :param engine_ctx: The execution context
        :param span: Tracing span for observability
        :return: NodeRunResult containing the execution result
        """
        engine_ctx.node_run_status[node.node_id].processing.set()
        return await node.async_call(
            variable_pool=engine_ctx.variable_pool,
            callbacks=engine_ctx.callback,
            span=span,
            iteration_engine=engine_ctx.iteration_engine,
            event_log_trace=engine_ctx.event_log_trace,
            msg_or_end_node_deps=engine_ctx.msg_or_end_node_deps,
            node_run_status=engine_ctx.node_run_status,
            chains=engine_ctx.chains,
            built_nodes=engine_ctx.built_nodes,
        )

    def can_handle(self, node_type: str) -> bool:
        """
        Default strategy can handle all node types.

        :param node_type: The type of the node
        :return: Always returns True
        """
        return True


class QuestionAnswerNodeStrategy(NodeExecutionStrategy):
    """
    Execution strategy for question-answer nodes.

    Ensures serial execution of question-answer nodes by using a lock
    to prevent concurrent execution.
    """

    async def execute_node(
        self,
        node: SparkFlowEngineNode,
        engine_ctx: WorkflowEngineCtx,
        span: Span,
    ) -> NodeRunResult:
        """
        Execute question-answer node with lock to ensure serial execution.

        :param node: The node to execute
        :param engine_ctx: The execution context
        :param span: Tracing span for observability
        :return: NodeRunResult containing the execution result
        """
        qa_node_lock = engine_ctx.qa_node_lock
        async with qa_node_lock:
            return await DefaultNodeExecutionStrategy().execute_node(
                node, engine_ctx, span
            )

    def can_handle(self, node_type: str) -> bool:
        """
        Handle question-answer node type.

        :param node_type: The type of the node
        :return: True if the node type is QUESTION_ANSWER, False otherwise
        """
        return node_type == NodeType.QUESTION_ANSWER.value


class NodeExecutionStrategyManager:
    """
    Manager for node execution strategies.

    Manages a collection of execution strategies and provides the appropriate
    strategy for a given node type using the Strategy pattern.
    """

    def __init__(self) -> None:
        self.strategies = [
            QuestionAnswerNodeStrategy(),
            DefaultNodeExecutionStrategy(),  # Default strategy placed last
        ]

    def get_strategy(self, node_type: str) -> NodeExecutionStrategy:
        """
        Get the appropriate execution strategy for a node type.

        :param node_type: The type of the node
        :return: The appropriate execution strategy for the node type
        """
        for strategy in self.strategies:
            if strategy.can_handle(node_type):
                return strategy

        # If no suitable strategy is found, return the default strategy
        return DefaultNodeExecutionStrategy()


class WorkflowEngine(BaseModel):
    """
    Main workflow execution engine.

    Orchestrates the execution of workflow nodes using depth-first search,
    manages error handling, retry mechanisms, and provides various execution
    strategies for different node types.
    """

    engine_ctx: WorkflowEngineCtx = None  # type: ignore

    # Currently running SparkFlow engine node
    sparkflow_engine_node: SparkFlowEngineNode

    # Set of node IDs that support streaming processing
    support_stream_node_ids: set = Field(default_factory=set)

    # Maximum token configuration for model nodes (key: node_id, value: max_tokens)
    node_max_token: dict = Field(default_factory=dict)

    # Workflow DSL definition describing the structure and logic of the entire workflow
    workflow_dsl: WorkflowDSL

    # End node output mode (default is VARIABLE_MODE)
    end_node_output_mode: EndNodeOutputModeEnum = EndNodeOutputModeEnum.VARIABLE_MODE

    strategy_manager: NodeExecutionStrategyManager
    error_handler_chain: ErrorHandlerChain

    class Config:
        arbitrary_types_allowed = True

    def __init__(self, **data: Any) -> None:
        super().__init__(
            strategy_manager=NodeExecutionStrategyManager(),
            error_handler_chain=ErrorHandlerChain(),
            **data,
        )

    async def async_run(
        self,
        inputs: dict,
        span: Span,
        callback: ChatCallBacks,
        history: list,
        history_v2: list[HistoryItem],
        event_log_trace: WorkflowLog,
    ) -> NodeRunResult:
        """
        Execute the workflow asynchronously.

        :param inputs: Input parameters for the workflow
        :param span: Tracing span for observability
        :param callback: Callback handler for workflow events
        :param history: Historical conversation data
        :param history_v2: Historical conversation data (version 2)
        :param event_log_trace: Event log trace for workflow execution tracking
        :return: NodeRunResult containing the final execution result
        """

        with span.start("engine_async_run") as span_context:
            # Initialize parameters
            if self.sparkflow_engine_node.node_id.startswith(NodeType.START.value):
                self.engine_ctx.qa_node_lock = asyncio.Lock()
                for _, iter_eng in self.engine_ctx.iteration_engine.items():
                    iter_eng.engine_ctx.qa_node_lock = self.engine_ctx.qa_node_lock
            self.engine_ctx.end_complete = asyncio.Event()
            self.engine_ctx.callback = callback
            self.engine_ctx.event_log_trace = event_log_trace

            self._validate_start_node()
            await self._initialize_variable_pool_with_start_node(
                inputs, span, callback, history, history_v2
            )

            # Execute the workflow
            return await self._execute_workflow_internal(span_context)

    async def _execute_workflow_internal(self, span: Span) -> NodeRunResult:
        """
        Internal workflow execution logic.

        :param span: Tracing span for observability
        :return: NodeRunResult containing the final execution result
        """

        # Start depth-first search execution
        await self._depth_first_search_execution(self.sparkflow_engine_node, span)

        # Wait for completion
        await self.engine_ctx.end_complete.wait()

        # Wait for all tasks to complete
        await self._wait_all_tasks_completion(self.engine_ctx.dfs_tasks)

        return self.engine_ctx.responses[0]

    async def _handle_node_start_callback(
        self,
        node: SparkFlowEngineNode,
    ) -> None:
        """
        Handle node start callback.

        :param node: The node that is starting
        :return: None
        """
        node_type = node.node_id.split("::")[0]

        # For SSE interface and message nodes, let the message node control start and end frames
        if node_type in [
            NodeType.MESSAGE.value,
            NodeType.END.value,
        ]:
            return

        await self.engine_ctx.callback.on_node_start(
            code=0, node_id=node.node_id, alias_name=node.node_alias_name
        )

    async def _execute_single_node(
        self,
        node: SparkFlowEngineNode,
        span_context: Span,
    ) -> Tuple[List[SparkFlowEngineNode], Optional[NodeRunResult]]:
        """
        Execute a single node.

        :param node: The node to execute
        :param span_context: Tracing span for observability
        :return: Tuple of (next batch of nodes to execute, optional node run result)
        """
        # Wait for predecessor nodes to complete
        await self._wait_predecessor_nodes(node)

        run_result = None
        fail_branch = False

        # Check if node needs to be executed
        if not self.engine_ctx.node_run_status[node.node_id].processing.is_set():
            # Handle message node dependencies
            await self._handle_message_node_dependencies(node, span_context)

            # Node start callback
            await self._handle_node_start_callback(node)

            # Execute node
            run_result, fail_branch = await self._execute_node_with_retry(
                node, span_context
            )

            # Mark node as complete
            self.engine_ctx.node_run_status[node.node_id].complete.set()

        # Get next batch of active nodes
        next_active_nodes, next_inactive_nodes = await self._get_next_nodes(
            node, run_result, fail_branch
        )

        # Handle inactive nodes
        if next_inactive_nodes:
            await self._handle_inactive_nodes(node, next_inactive_nodes, span_context)

        # Handle end node
        if self._is_end_node(node):
            run_result = await self._handle_end_node(node, run_result)

        return next_active_nodes, run_result

    async def _handle_inactive_nodes(
        self,
        node: SparkFlowEngineNode,
        next_inactive_nodes: List[SparkFlowEngineNode],
        span_context: Span,
    ) -> None:
        """
        Handle inactive nodes.

        :param node: The current node
        :param next_inactive_nodes: List of nodes that should not be activated
        :param span_context: Tracing span for observability
        :return: None
        """
        not_run_node_ids = [n.id for n in next_inactive_nodes]
        await self._deactivate_node_paths(node.id, not_run_node_ids, span_context)
        await self._set_nodes_logical_run_status(not_run_node_ids, span_context)

    def _is_end_node(self, node: SparkFlowEngineNode) -> bool:
        """
        Check if the node is an end node.

        :param node: The node to check
        :return: True if the node is an end node, False otherwise
        """
        return node.node_id.startswith(NodeType.END.value) or node.node_id.startswith(
            NodeType.ITERATION_END.value
        )

    async def _handle_end_node(
        self,
        node: SparkFlowEngineNode,
        run_result: Optional[NodeRunResult],
    ) -> NodeRunResult:
        """
        Handle end node execution.

        :param node: The end node
        :param run_result: Optional existing run result
        :return: NodeRunResult containing the end node execution result
        """
        await self.engine_ctx.node_run_status[node.node_id].complete.wait()

        if run_result:
            return run_result

        # Find end node result from DFS tasks
        for task in self.engine_ctx.dfs_tasks:
            task_result = task.result()
            if task_result and (
                task_result.node_id.startswith(NodeType.END.value)
                or task_result.node_id.startswith(NodeType.ITERATION_END.value)
            ):
                return task_result

        raise CustomException(
            CodeEnum.EngRunErr, err_msg="End node did not return result"
        )

    async def _get_next_nodes(
        self,
        node: SparkFlowEngineNode,
        run_result: Optional[NodeRunResult],
        fail_branch: bool,
    ) -> Tuple[List[SparkFlowEngineNode], List[SparkFlowEngineNode]]:
        """
        Get the next batch of nodes to execute.

        :param node: The current node
        :param run_result: The result of the current node execution
        :param fail_branch: Whether the execution should follow the failure branch
        :return: Tuple of (active nodes, inactive nodes)
        """
        next_active_nodes, next_inactive_nodes = [], []
        node_type = node.id.split(":")[0]

        # Check if this is a branch type node
        branch_type = self._is_branch_node(node_type, node)

        if fail_branch:
            # Failure branch scenario
            next_active_nodes = node.get_fail_nodes()
            next_inactive_nodes = [
                item
                for item in node.get_next_nodes()
                if item not in node.get_fail_nodes()
            ]
        else:
            if branch_type:
                # Branch nodes need to select branch based on result
                if not run_result:
                    raise CustomException(
                        CodeEnum.EngRunErr, err_msg="Branch node did not return result"
                    )
                next_active_nodes = await self._handle_branch_node_logic(
                    node, run_result, node_type
                )
                next_inactive_nodes = [
                    n for n in node.next_nodes if n not in next_active_nodes
                ]
            else:
                # Regular node
                next_active_nodes = node.get_next_nodes()

            # Add failure branches to inactive nodes
            next_inactive_nodes.extend(
                [
                    item
                    for item in node.get_fail_nodes()
                    if item not in next_active_nodes
                ]
            )

        return next_active_nodes, next_inactive_nodes

    async def _handle_branch_node_logic(
        self,
        node: SparkFlowEngineNode,
        run_result: NodeRunResult,
        node_type: str,
    ) -> List[SparkFlowEngineNode]:
        """
        Handle branch node logic.

        :param node: The branch node
        :param run_result: The result of the branch node execution
        :param node_type: The type of the node
        :return: List of next nodes to execute based on branch logic
        """
        edge_source_handle = run_result.dict().get(
            "edge_source_handle", "default_chain"
        )
        intents = node.get_classify_class().get(edge_source_handle)

        # Default handling for question classification nodes
        if node_type == NodeType.DECISION_MAKING.value and not intents:
            intents = self._get_default_intent_chain(node)

        if not intents:
            raise CustomException(
                CodeEnum.EngRunErr,
                err_msg=f"Branch not found: {intents}",
            )

        # Select next nodes based on intent
        return [n for n in node.next_nodes if n.id in intents]

    def _get_default_intent_chain(
        self, node: SparkFlowEngineNode
    ) -> Optional[List[str]]:
        """
        Get the default intent chain.

        :param node: The node to get the default intent chain for
        :return: List of default intent chain node IDs, or None if not found
        """
        intent_chains = node.node_instance.get_node_config().get("intentChains", [])

        for intent in intent_chains:
            if intent.get("name", "") == "default":
                default_id = intent.get("id", "")
                return node.get_classify_class().get(default_id)

        return None

    def _is_branch_node(self, node_type: str, node: SparkFlowEngineNode) -> bool:
        """
        Check if the node is a branch node.

        :param node_type: The type of the node
        :param node: The node instance
        :return: True if the node is a branch node, False otherwise
        """
        if node_type in (NodeType.DECISION_MAKING.value, NodeType.IF_ELSE.value):
            return True

        if node_type == NodeType.QUESTION_ANSWER.value:
            instance = node.node_instance
            answer_type = instance.get_node_config().get("answerType")
            return answer_type == "option"

        return False

    async def _execute_node_with_retry(
        self,
        node: SparkFlowEngineNode,
        span_context: Span,
    ) -> Tuple[NodeRunResult, bool]:
        """
        Execute node with retry mechanism.

        :param node: The node to execute
        :param span_context: Tracing span for observability
        :return: Tuple of (node run result, whether this is a failure branch)
        """
        node_type = node.node_id.split("::")[0]
        retry_config = node.node_instance.retry_config

        # Check if error handling is needed
        need_error_handling = (
            node_type
            in (
                CONTINUE_ON_ERROR_STREAM_NODE_TYPE
                + CONTINUE_ON_ERROR_NOT_STREAM_NODE_TYPE
            )
            and retry_config.should_retry
        )

        if need_error_handling:
            return await self._execute_with_error_handling(node, span_context)

        return await self._execute_without_error_handling(node, span_context)

    async def _execute_without_error_handling(
        self,
        node: SparkFlowEngineNode,
        span_context: Span,
    ) -> Tuple[NodeRunResult, bool]:
        """
        Execute node without error handling.

        :param node: The node to execute
        :param span_context: Tracing span for observability
        :return: Tuple of (node run result, False for no failure branch)
        """

        try:
            strategy = self.strategy_manager.get_strategy(node.node_id.split("::")[0])
            run_result = await strategy.execute_node(
                node, self.engine_ctx, span_context
            )
            return run_result, False

        except CustomException as err:
            await self.engine_ctx.callback.on_node_end(
                node_id=node.node_id,
                alias_name=node.node_alias_name,
                error=err,
            )
            raise err

        except Exception as err:
            custom_err = CustomException(
                CodeEnum.NodeRunErr, err_msg=f"{err}", cause_error=f"{err}"
            )
            await self.engine_ctx.callback.on_node_end(
                node_id=node.node_id, alias_name=node.node_alias_name, error=custom_err
            )
            raise custom_err from err

    async def _execute_with_error_handling(
        self,
        node: SparkFlowEngineNode,
        span_context: Span,
    ) -> Tuple[NodeRunResult, bool]:
        """
        Execute node with error handling and retry mechanism.

        :param node: The node to execute
        :param span_context: Tracing span for observability
        :return: Tuple of (node run result, whether this is a failure branch)
        """
        retry_config = node.node_instance.retry_config
        max_retries = retry_config.max_retries
        node_type = node.node_id.split("::")[0]

        for attempt in range(max_retries + 1):
            try:
                # Select execution method based on node type
                if node_type in CONTINUE_ON_ERROR_NOT_STREAM_NODE_TYPE:
                    run_result = await self._execute_non_stream_node(
                        node, span_context, retry_config
                    )
                else:
                    run_result = await self._execute_stream_node(node, span_context)

                # Check execution result
                if run_result.status == WorkflowNodeExecutionStatus.SUCCEEDED:
                    return run_result, False

                # If not successful status, raise exception to enter retry logic
                raise CustomException(
                    CodeEnum.NodeRunErr,
                    err_msg=f"{run_result.error}",
                    cause_error=f"{run_result.error}",
                )

            except Exception as error:
                # Use chain of responsibility to handle errors
                result, should_retry = await self.error_handler_chain.handle_error(
                    error, node, self.engine_ctx, attempt, span_context
                )

                if result is not None:
                    # Error handler returned a result
                    fail_branch = (
                        retry_config.error_strategy == ErrorHandler.FailBranch.value
                    )
                    return result, fail_branch

                if not should_retry:
                    # No need to retry, re-raise exception
                    raise error

        # Should not reach here
        raise RuntimeError("Unexpected end of retry loop")

    async def _execute_non_stream_node(
        self,
        node: SparkFlowEngineNode,
        span_context: Span,
        retry_config: RetryConfig,
    ) -> NodeRunResult:
        """
        Execute non-streaming node with timeout.

        :param node: The node to execute
        :param span_context: Tracing span for observability
        :param retry_config: Retry configuration for the node
        :return: NodeRunResult containing the execution result
        """
        try:
            strategy = self.strategy_manager.get_strategy(node.node_id.split("::")[0])
            return await asyncio.wait_for(
                strategy.execute_node(node, self.engine_ctx, span_context),
                timeout=retry_config.timeout,
            )
        except asyncio.TimeoutError as e:
            raise CustomException(
                CodeEnum.NodeRunErr,
                err_msg="Node execution timeout",
                cause_error="Node execution timeout",
            ) from e

    async def _execute_stream_node(
        self,
        node: SparkFlowEngineNode,
        span_context: Span,
    ) -> NodeRunResult:
        """
        Execute streaming node with failure node cancellation handling.

        :param node: The node to execute
        :param span_context: Tracing span for observability
        :return: NodeRunResult containing the execution result
        """

        # Create waiting task to handle cancellation of failure nodes
        async def wait_and_deactivate() -> None:
            await node.node_instance.stream_node_first_token.wait()
            cancel_error_node_ids = [
                n.id for n in node.fail_nodes if n not in node.next_nodes
            ]
            await self._deactivate_node_paths(
                node.id, cancel_error_node_ids, span_context
            )
            await self._set_nodes_logical_run_status(
                cancel_error_node_ids, span_context
            )

        task = asyncio.create_task(wait_and_deactivate())
        self.engine_ctx.dfs_tasks.append(task)
        strategy = self.strategy_manager.get_strategy(node.node_id.split("::")[0])
        return await strategy.execute_node(node, self.engine_ctx, span_context)

    async def _depth_first_search_execution(
        self,
        node: SparkFlowEngineNode,
        span: Span,
    ) -> None:
        """
        Execute node using depth-first search algorithm.

        :param node: The node to execute
        :param span: Tracing span for observability
        :return: None
        """
        with span.start() as dfs_span:
            # Check if node is already being processed
            node_status = self.engine_ctx.node_run_status[node.node_id]
            if (
                node_status.processing.is_set()
                and not node_status.pre_processing.is_set()
            ):
                return

            try:
                # Execute the node
                next_active_nodes, run_result = await self._execute_single_node(
                    node, dfs_span
                )

                # Handle execution result
                await self._handle_node_execution_result(
                    next_active_nodes, run_result, dfs_span
                )

            except Exception as e:
                dfs_span.add_error_event(f"Node execution error: {e}")
                self.engine_ctx.end_complete.set()
                raise e

    async def _handle_node_execution_result(
        self,
        next_active_nodes: List[SparkFlowEngineNode],
        run_result: Optional[NodeRunResult],
        span_context: Span,
    ) -> None:
        """
        Handle node execution result and schedule next nodes.

        :param next_active_nodes: List of next nodes to execute
        :param run_result: Result of the current node execution
        :param span_context: Tracing span for observability
        :return: None
        """
        if not next_active_nodes:
            # No next nodes, set as successful and complete
            if run_result:
                run_result.status = WorkflowNodeExecutionStatus.SUCCEEDED
                self.engine_ctx.responses.append(run_result)
            self.engine_ctx.end_complete.set()
            return

        # Create execution tasks for each next node
        for next_node in next_active_nodes:
            if not self.engine_ctx.node_run_status[
                next_node.node_id
            ].start_with_thread.is_set():
                self.engine_ctx.node_run_status[
                    next_node.node_id
                ].start_with_thread.set()

                task = asyncio.create_task(
                    self._depth_first_search_execution(next_node, span_context)
                )
                self.engine_ctx.dfs_tasks.append(task)

    async def _wait_all_tasks_completion(self, dfs_tasks: List[Task]) -> None:
        """
        Wait for all DFS tasks to complete.

        :param dfs_tasks: List of depth-first search tasks
        :return: None
        """
        if not dfs_tasks:
            return

        done, pending = await asyncio.wait(
            dfs_tasks, return_when=asyncio.FIRST_EXCEPTION
        )

        # Cancel all pending tasks
        for task in pending:
            task.cancel()

        # Check if completed tasks have exceptions
        try:
            for task in done:
                task.result()
        except Exception:
            raise

    def _validate_start_node(self) -> None:
        """
        Validate that the start node is of correct type.

        :return: None
        :raises CustomException: If start node type is invalid
        """
        start_node_type = self.sparkflow_engine_node.id.split(":")[0]
        valid_start_types = [NodeType.START.value, NodeType.ITERATION_START.value]

        if start_node_type not in valid_start_types:
            raise CustomException(
                CodeEnum.EngRunErr,
                err_msg=f"Node:{self.sparkflow_engine_node.id} is not a start node",
            )

    async def _initialize_variable_pool_with_start_node(
        self,
        inputs: Dict[str, Any],
        span: Span,
        callback: ChatCallBacks,
        history: List,
        history_v2: List[HistoryItem],
    ) -> None:
        """
        Initialize variable pool with start node inputs and history.

        :param inputs: Input parameters for the workflow
        :param span: Tracing span for observability
        :param callback: Callback handler for workflow events
        :param history: Historical conversation data
        :param history_v2: Historical conversation data (version 2)
        :return: None
        :raises CustomException: If initialization fails
        """
        try:
            self.engine_ctx.variable_pool.add_init_variable(
                node_id=self.sparkflow_engine_node.id,
                key_name_list=list(inputs.keys()),
                value=inputs,
                span=span,
            )
            self.engine_ctx.variable_pool.add_history(history)
            self.engine_ctx.variable_pool.add_init_history(history_v2)
        except Exception as e:
            ce = CustomException(
                err_code=CodeEnum.StartNodeSchemaError,
                err_msg=str(e),
                cause_error=str(e),
            )
            await callback.on_node_end(
                node_id=self.sparkflow_engine_node.id,
                alias_name=self.sparkflow_engine_node.node_alias_name,
                error=ce,
            )
            raise ce from e

    def _get_error_llm_content(
        self, node_type: str, node: SparkFlowEngineNode
    ) -> Dict[str, Any]:
        """
        Get LLM content for error scenarios.

        :param node_type: The type of the node
        :param node: The node instance
        :return: Dictionary containing error LLM content
        """
        match node_type:
            case NodeType.AGENT.value:
                return {
                    "code": -1,
                    "choices": [{"finish_reason": "stop"}],
                }
            case NodeType.KNOWLEDGE_PRO.value:
                return {
                    "code": -1,
                    "finish_reason": "stop",
                }
            case NodeType.FLOW.value:
                return {
                    "code": -1,
                    "choices": [{"finish_reason": "stop"}],
                }
            case NodeType.LLM.value:
                model_source = node.node_instance.get_node_config().get(
                    "source", ModelProviderEnum.XINGHUO.value
                )
                match model_source:
                    case ModelProviderEnum.XINGHUO.value:
                        return {
                            "header": {
                                "code": -1,
                                "status": XFLLMStatus.END.value,
                            },
                            "payload": {"choices": {"text": [{}]}},
                        }
                    case ModelProviderEnum.OPENAI.value:
                        return {
                            "code": -1,
                            "choices": [{"finish_reason": "stop"}],
                        }
            case _:
                return {"code": -1}
        return {"code": -1}

    async def _handle_message_node_dependencies(
        self,
        node: SparkFlowEngineNode,
        span_context: Span,
    ) -> None:
        """
        Handle message node dependencies for the current node.

        :param node: The current node
        :param span_context: Tracing span for observability
        :return: None
        """
        node_type = node.node_id.split("::")[0]
        if node_type in [NodeType.START.value, NodeType.ITERATION_START.value]:
            return

        # Check message or end node dependencies
        for msg_node_id, dep in self.engine_ctx.msg_or_end_node_deps.items():
            data_dep_path_info = (
                dep.data_dep_path_info.get(node.node_id, False)
                if hasattr(dep, "data_dep_path_info")
                else True
            )

            should_execute_message_node = (
                node.node_id in dep.data_dep
                and not self.engine_ctx.node_run_status[
                    msg_node_id
                ].pre_processing.is_set()
                and data_dep_path_info
            )

            if should_execute_message_node:
                self.engine_ctx.node_run_status[msg_node_id].pre_processing.set()

                # Create message node execution task
                task = asyncio.create_task(
                    self._execute_message_node(msg_node_id, span_context)
                )
                self.engine_ctx.dfs_tasks.append(task)

    async def _execute_message_node(
        self,
        msg_node_id: str,
        span_context: Span,
    ) -> NodeRunResult:
        """
        Execute a message node.

        :param msg_node_id: The ID of the message node to execute
        :param span_context: Tracing span for observability
        :return: NodeRunResult containing the execution result
        """
        try:
            node = self.engine_ctx.built_nodes[msg_node_id]
            strategy = self.strategy_manager.get_strategy(node.node_id.split("::")[0])
            return await strategy.execute_node(node, self.engine_ctx, span_context)
        finally:
            self.engine_ctx.node_run_status[msg_node_id].complete.set()

    async def _deactivate_node_paths(
        self, current_node_id: str, node_ids: list, span: Span
    ) -> None:
        with span.start("deactivate_branch_paths") as span_context:
            for node_id in node_ids:
                node_chains = self.engine_ctx.chains.get_branch_chains(
                    current_node_id, node_id
                )
                for simple_path in node_chains:
                    if not simple_path.inactive.is_set():
                        simple_path.inactive.set()
                        span_context.add_info_events(
                            {"inactive": simple_path.node_id_list}
                        )

    async def _set_nodes_logical_run_status(
        self, not_run_node_ids: List[str], span: Span
    ) -> None:
        """
        Set logical run status for nodes that should not run.

        :param not_run_node_ids: List of node IDs that should not run
        :param span: Tracing span for observability
        :return: None
        """
        for not_run_node_id in not_run_node_ids:
            # Get chains that the current node belongs to
            chains_of_node = self.engine_ctx.chains.get_node_chains(
                not_run_node_id
            ) or self.engine_ctx.chains.get_node_chains_with_node_id(not_run_node_id)

            # Check if there are any active chains
            node_should_not_run = True
            for chain in chains_of_node:
                if not chain.inactive.is_set():
                    node_should_not_run = False
                    break

            if node_should_not_run:
                # Set node status
                node_status = self.engine_ctx.node_run_status[not_run_node_id]
                node_status.not_run.set()
                node_status.processing.set()
                node_status.complete.set()
                node_status.start_with_thread.set()

                if span:
                    span.add_info_events({"not_run_node_id": not_run_node_id})

                # Recursively process subsequent nodes
                if not self._is_terminal_node(not_run_node_id):
                    try:
                        next_node_ids = self.engine_ctx.chains.edge_dict[
                            not_run_node_id
                        ]
                        await self._set_nodes_logical_run_status(next_node_ids, span)
                    except Exception as e:
                        raise e

    def _is_terminal_node(self, node_id: str) -> bool:
        """
        Check if the node is a terminal node.

        :param node_id: The ID of the node to check
        :return: True if the node is a terminal node, False otherwise
        """
        return node_id.startswith(NodeType.END.value) or node_id.startswith(
            NodeType.ITERATION_END.value
        )

    async def _wait_predecessor_nodes(
        self,
        node: SparkFlowEngineNode,
    ) -> None:
        """
        Wait for predecessor nodes to complete.

        :param node: The node to wait for predecessors
        :return: None
        """
        node_type = node.id.split(":")[0]
        if node_type in [NodeType.START.value, NodeType.ITERATION_START.value]:
            return

        node_chains = self.engine_ctx.chains.get_node_chains(node.node_id)
        tasks = []

        for simple_path in node_chains:
            if simple_path.inactive.is_set():
                continue

            # Create waiting tasks for each predecessor node
            for i in range(len(simple_path.node_id_list) - 1):
                pre_node_id, current_node_id = (
                    simple_path.node_id_list[i],
                    simple_path.node_id_list[i + 1],
                )

                if current_node_id == node.node_id:
                    tasks.extend(
                        self._create_predecessor_wait_tasks(
                            node, pre_node_id, simple_path
                        )
                    )

        if tasks:
            await asyncio.wait(tasks)

    def _create_predecessor_wait_tasks(
        self,
        node: SparkFlowEngineNode,
        pre_node_id: str,
        simple_path: SimplePath,
    ) -> List[Task]:
        """
        Create waiting tasks for predecessor nodes.

        :param node: The current node
        :param pre_node_id: The ID of the predecessor node
        :param simple_path: The simple path containing the nodes
        :return: List of asyncio tasks for waiting
        """
        tasks = []
        pre_nodes = node.get_pre_nodes()

        for pre_node in pre_nodes:
            if pre_node.node_id == pre_node_id:
                wait_task = asyncio.create_task(
                    wait_at_least_one_task_completed(
                        [
                            asyncio.create_task(
                                self.engine_ctx.node_run_status[
                                    pre_node.node_id
                                ].complete.wait()
                            ),
                            asyncio.create_task(simple_path.inactive.wait()),
                        ]
                    )
                )
                tasks.append(wait_task)

        return tasks

    def dumps(self, span: Span) -> bytes:
        """
        Serialize the engine to bytes.

        :param span: Tracing span for observability
        :return: Serialized engine as bytes
        """

        try:
            content = pickle.dumps(self)
            return content
        except Exception as e:
            # External exception caught, do not return to user
            span.record_exception(e)
            return b""

    @staticmethod
    def loads(
        build_result: bytes, span: Span
    ) -> Tuple[Optional["WorkflowEngine"], int]:
        """
        Deserialize engine from bytes.

        :param build_result: Byte object generated by pickle.dumps
        :param span: Tracing span for observability
        :return: Tuple of (engine instance, build timestamp)
        """
        try:
            engine_cache_entity: WorkflowEngine = pickle.loads(build_result)
            return engine_cache_entity, engine_cache_entity.engine_ctx.build_timestamp
        except Exception as e:
            # External exception caught, do not return to user
            span.record_exception(e)
            return None, 0


class WorkflowEngineFactory:
    """
    Factory for creating workflow engines.

    Provides static methods to create workflow engines and debug nodes
    from workflow DSL definitions.
    """

    @staticmethod
    def create_engine(
        sparkflow_dsl: WorkflowDSL,
        span: Span,
    ) -> WorkflowEngine:
        """
        Create a workflow engine.

        :param sparkflow_dsl: Workflow DSL definition
        :param span: Tracing span for observability
        :return: WorkflowEngine instance
        """
        with span.start() as span_context:
            builder = (
                WorkflowEngineBuilder(sparkflow_dsl)
                .build_chains()
                .build_nodes(span_context)
                .build_node_dependencies()
                .build_node_status()
                .build_message_dependencies()
            )

            return builder.build()

    @staticmethod
    def create_debug_node(
        sparkflow_dsl: WorkflowDSL,
        span: Span,
    ) -> BaseNode:
        """
        Create a debug node.

        :param sparkflow_dsl: Workflow DSL definition
        :param span: Tracing span for observability
        :return: BaseNode instance for debugging
        """
        with span.start() as span_context:
            builder = WorkflowEngineBuilder(sparkflow_dsl).build_nodes(span_context)
            if len(sparkflow_dsl.nodes) == 0:
                raise ValueError("WorkflowDSL must have at least one node.")
            builder.start_node_id = sparkflow_dsl.nodes[0].id
            builder.chains = Chains(workflow_schema=sparkflow_dsl)
            engine = builder.build()
            if (
                engine.engine_ctx
                and builder.start_node_id in engine.engine_ctx.built_nodes
            ):
                return engine.engine_ctx.built_nodes[
                    builder.start_node_id
                ].node_instance
            else:
                raise ValueError(f"Start node ({builder.start_node_id}) not found.")


class WorkflowEngineBuilder:
    """
    Builder for constructing workflow engines.

    Implements the Builder pattern to construct workflow engines step by step,
    including building chains, nodes, dependencies, and execution status.
    """

    chains: Chains

    def __init__(self, sparkflow_dsl: WorkflowDSL):
        self.sparkflow_dsl: WorkflowDSL = sparkflow_dsl
        self.built_nodes: Dict[str, SparkFlowEngineNode] = {}
        self.start_node_id: str = ""
        self.variable_pool = VariablePool(sparkflow_dsl.nodes)
        self.iteration_engine_nodes: Dict[str, str] = {}
        self.iteration_engine: Dict[str, WorkflowEngine] = {}
        self.end_node_output_mode = EndNodeOutputModeEnum.VARIABLE_MODE
        self.node_max_token: Dict[str, int] = {}
        self.msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo] = {}
        self.node_run_status: Dict[str, NodeRunningStatus] = {}

    def build(self) -> WorkflowEngine:
        """
        Build the workflow engine.

        :return: Complete WorkflowEngine instance
        """
        support_stream_node_ids = set()

        # End nodes need to output last, regardless of streaming support, add to set
        for node_id in self.built_nodes:
            if node_id.split("::")[0] in [
                NodeType.END.value,
                NodeType.MESSAGE.value,
            ]:
                support_stream_node_ids.add(node_id)

        for _, iteration_engine in self.iteration_engine.items():
            iteration_engine.engine_ctx = WorkflowEngineCtx(
                variable_pool=self.variable_pool,
                iteration_engine=self.iteration_engine,
                msg_or_end_node_deps=self.msg_or_end_node_deps,
                node_run_status=self.node_run_status,
                built_nodes=self.built_nodes,
                chains=self.chains,
            )

        return WorkflowEngine(
            engine_ctx=WorkflowEngineCtx(
                variable_pool=self.variable_pool,
                iteration_engine=self.iteration_engine,
                msg_or_end_node_deps=self.msg_or_end_node_deps,
                node_run_status=self.node_run_status,
                built_nodes=self.built_nodes,
                chains=self.chains,
            ),
            sparkflow_engine_node=self.built_nodes[self.start_node_id],
            support_stream_node_ids=support_stream_node_ids,
            node_max_token=self.node_max_token,
            workflow_dsl=self.sparkflow_dsl,
            end_node_output_mode=self.end_node_output_mode,
        )

    def build_nodes(self, span_context: Span) -> "WorkflowEngineBuilder":
        """
        Build SparkFlow nodes.

        :param span_context: Tracing span for observability
        :return: Self for method chaining
        """
        for node in self.sparkflow_dsl.nodes:

            # Create engine node
            spark_node_instance = self._create_engine_node(
                node_id=node.id, span_context=span_context
            )

            # Handle special node types
            self._handle_special_node_types(node, spark_node_instance)

            # Check for duplicate nodes
            if node.id in self.built_nodes:
                raise CustomException(
                    CodeEnum.EngBuildErr,
                    err_msg=f"Node: {node.id} duplicate build",
                )
            self.built_nodes[node.id] = spark_node_instance

        # Handle iteration engine nodes
        self._build_iteration_engines()

        return self

    def build_chains(self) -> "WorkflowEngineBuilder":
        """
        Build execution chains.

        :return: Self for method chaining
        """
        self.chains = Chains(workflow_schema=self.sparkflow_dsl)
        self.chains.gen()
        return self

    def build_node_dependencies(self) -> "WorkflowEngineBuilder":
        """
        Build node dependencies.

        :return: Self for method chaining
        """
        for edge in self.sparkflow_dsl.edges:
            self._build_single_edge_dependency(edge)
        return self

    def build_message_dependencies(self) -> "WorkflowEngineBuilder":
        """
        Build message dependencies.

        :return: Self for method chaining
        """
        msg_or_end_node_deps_list = []

        # Get main chain message dependencies
        for chain in self.chains.master_chains:
            msg_or_end_node_dep: Dict[str, MsgOrEndDepInfo] = {}
            for node_id in reversed(chain.node_id_list):
                self._build_node_message_dependency(node_id, msg_or_end_node_dep)
            msg_or_end_node_deps_list.append(msg_or_end_node_dep)

        # Handle iteration chain message dependencies
        for iteration_chain in self.chains.iteration_chains.values():
            for chain in iteration_chain.master_chains:
                msg_or_end_node_dep_iter: Dict[str, MsgOrEndDepInfo] = {}
                for node_id in reversed(chain.node_id_list):
                    self._build_node_message_dependency(
                        node_id, msg_or_end_node_dep_iter
                    )
                msg_or_end_node_deps_list.append(msg_or_end_node_dep_iter)

        # Merge message dependencies
        self._merge_message_dependencies(msg_or_end_node_deps_list)

        # Build data dependencies
        self._build_data_dependencies()

        return self

    def build_node_status(self) -> "WorkflowEngineBuilder":
        """
        Build node running status.

        :return: Self for method chaining
        """
        for node in self.sparkflow_dsl.nodes:
            if node.id:
                self.node_run_status[node.id] = NodeRunningStatus()
        return self

    def _validate_node(self, node_id: str, node: Node) -> None:
        """
        Validate node configuration.

        :param node_id: The ID of the node to validate
        :param node: The node instance to validate
        :return: None
        :raises CustomException: If node validation fails
        """
        node_type = node_id.split(":")[0]
        node_class = tool_classes.get(node_type)

        if not node_class:
            raise CustomException(
                CodeEnum.EngNodeProtocolValidateErr,
                err_msg=f"Current workflow does not support node type: {node_type}",
            )

        # Validate node configuration
        errs = BaseNode.schema_validate(node_body=node.dict(), node_type=node_type)
        if errs:
            raise CustomException(
                CodeEnum.EngNodeProtocolValidateErr,
                err_msg=f"Node: {node_id} configuration parameter validation failed, reason: {errs}",
            )

    def _create_engine_node(
        self, node_id: str, span_context: Span
    ) -> SparkFlowEngineNode:
        """
        Create an engine node.

        :param node_id: The ID of the node to create
        :param span_context: Tracing span for observability
        :return: SparkFlowEngineNode instance
        """
        node = self.sparkflow_dsl.check_nodes_exist(node_id)
        # Validate node configuration
        self._validate_node(node_id, node)
        return NodeFactory.create(node, span_context)

    def _build_iteration_engines(self) -> None:
        """
        Build iteration engines for iteration nodes.

        :return: None
        :raises CustomException: If iteration start node is not found
        """
        for (
            iteration_start_node_id,
            _,
        ) in self.iteration_engine_nodes.items():
            if iteration_start_node_id not in self.built_nodes:
                raise CustomException(
                    CodeEnum.EngBuildErr,
                    err_msg=f"Iteration start node: {iteration_start_node_id} does not exist",
                    cause_error=f"Iteration start node: {iteration_start_node_id} does not exist",
                )

            self.iteration_engine[iteration_start_node_id] = WorkflowEngine(
                sparkflow_engine_node=self.built_nodes[iteration_start_node_id],
                workflow_dsl=self.sparkflow_dsl,
            )

    def _handle_special_node_types(
        self, node: Node, spark_node_instance: SparkFlowEngineNode
    ) -> None:
        """
        Handle special node types.

        :param node: The node instance
        :param spark_node_instance: The SparkFlow engine node instance
        :return: None
        """
        node_type = node.get_node_type()

        if node_type == NodeType.START.value:
            self.start_node_id = node.id
        elif node_type == NodeType.DECISION_MAKING.value:
            self._handle_DECISION_MAKING_node(node.id, node)
        elif node_type == NodeType.LLM.value:
            self._handle_llm_node(node.id, node)
        elif node_type == NodeType.ITERATION.value:
            self._handle_iteration_node(node.id, node)
        elif node_type == NodeType.END.value:
            self._handle_end_node(spark_node_instance)

    def _handle_DECISION_MAKING_node(self, node_id: str, node: Node) -> None:
        """
        Handle decision making node.

        :param node_id: The ID of the decision making node
        :param node: The node instance
        :return: None
        :raises CustomException: If intent chains are missing
        """
        classes = node.data.nodeParam.get("intentChains")
        if not classes:
            raise CustomException(
                CodeEnum.EngNodeProtocolValidateErr,
                err_msg=f"Decision node: {node_id} intent does not exist",
                cause_error=f"Decision node: {node_id} intent does not exist",
            )

        self.node_max_token[node_id] = int(node.data.nodeParam.get("maxTokens", "0"))

    def _handle_llm_node(self, node_id: str, node: Node) -> None:
        """
        Handle LLM node.

        :param node_id: The ID of the LLM node
        :param node: The node instance
        :return: None
        """
        self.node_max_token[node_id] = int(node.data.nodeParam.get("maxTokens", "0"))

    def _handle_iteration_node(self, node_id: str, node: Node) -> None:
        """
        Handle iteration node.

        :param node_id: The ID of the iteration node
        :param node: The node instance
        :return: None
        :raises CustomException: If iteration start node ID is missing
        """
        iteration_start_node_id = node.data.nodeParam.get("IterationStartNodeId", "")
        if not iteration_start_node_id:
            raise CustomException(
                CodeEnum.EngNodeProtocolValidateErr,
                err_msg=f"Iteration node: {node.id} iteration start node does not exist",
            )
        self.iteration_engine_nodes[iteration_start_node_id] = node_id

    def _handle_end_node(self, spark_node_instance: SparkFlowEngineNode) -> None:
        """
        Handle end node.

        :param spark_node_instance: The SparkFlow engine node instance
        :return: None
        """
        end_node_config = spark_node_instance.node_instance.get_node_config()
        if end_node_config["outputMode"] == 0:
            self.end_node_output_mode = EndNodeOutputModeEnum.VARIABLE_MODE
        else:
            self.end_node_output_mode = EndNodeOutputModeEnum.PROMPT_MODE

    def _build_single_edge_dependency(self, edge: Edge) -> None:
        """
        Build dependency for a single edge.

        :param edge: Edge dictionary containing source and target node information
        :return: None
        :raises CustomException: If source or target node is not found
        """
        source_node_id = edge.sourceNodeId
        source_handle = edge.sourceHandle
        target_node_id = edge.targetNodeId

        source_node = self.built_nodes.get(source_node_id)
        target_node = self.built_nodes.get(target_node_id)

        if not source_node:
            raise CustomException(
                CodeEnum.EngBuildErr,
                err_msg=f"Node not found {source_node_id}",
                cause_error=f"Node not found {source_node_id}",
            )

        if not target_node:
            raise CustomException(
                CodeEnum.EngBuildErr,
                err_msg=f"Node not found {target_node_id}",
                cause_error=f"Node not found {target_node_id}",
            )

        # Handle source handle
        if source_handle and source_handle.startswith("intent_chain|"):
            source_handle = source_handle.split("|")[1]

        # Build dependency relationship based on handle type
        if "fail_one_of" in source_handle:
            source_node.add_fail_node(target_node)
            target_node.add_pre_node(source_node)
        else:
            source_node.add_next_node(target_node)
            target_node.add_pre_node(source_node)
            if source_handle:
                source_node.add_classify_class(source_handle, target_node_id)

    def _build_data_dependencies(self) -> None:
        """
        Build data dependencies.

        :return: None
        """
        for node in self.sparkflow_dsl.nodes:
            if not (
                node.id.startswith(NodeType.MESSAGE.value)
                or node.id.startswith(NodeType.END.value)
            ):
                continue

            inputs = node.data.inputs
            for input_item in inputs:
                var_type = input_item.get("schema", {}).get("value", {}).get("type")
                if var_type == "literal":
                    continue

                ref_node_id = (
                    input_item.get("schema", {})
                    .get("value", {})
                    .get("content", {})
                    .get("nodeId", "")
                )

                if ref_node_id:
                    self.msg_or_end_node_deps[node.id].data_dep.add(ref_node_id)

                    # Check if normal path exists
                    if self._has_normal_path(ref_node_id, node.id):
                        self.msg_or_end_node_deps[node.id].data_dep_path_info[
                            ref_node_id
                        ] = True

    def _has_normal_path(self, source: str, target: str) -> bool:
        """
        Check if there is a normal path (non-failure path) between source and target.

        :param source: Source node ID
        :param target: Target node ID
        :return: True if normal path exists, False otherwise
        """
        # Build graph
        graph: Dict[str, List[Dict[str, str]]] = {}
        for edge in self.sparkflow_dsl.edges:
            src = edge.sourceNodeId
            tgt = edge.targetNodeId
            if src not in graph:
                graph[src] = []
            graph[src].append({"target": tgt, "handle": edge.sourceHandle})

        visited = set()

        def dfs(node: str) -> bool:
            if node == target:
                return True

            visited.add(node)

            for edge in graph.get(node, []):
                next_node = edge["target"]
                if next_node in visited:
                    continue

                # Skip failure paths
                if edge.get("handle") and "fail_one_of" in edge.get("handle", ""):
                    continue

                if dfs(next_node):
                    return True

            visited.remove(node)
            return False

        return dfs(source)

    def _merge_message_dependencies(
        self, msg_or_end_node_deps_list: List[Dict]
    ) -> None:
        """
        Merge message dependencies.

        :param msg_or_end_node_deps_list: List of message or end node dependencies
        :return: None
        """
        for msg_or_end_node_dep in msg_or_end_node_deps_list:
            for node_id, node_dep_info in msg_or_end_node_dep.items():
                if node_id not in self.msg_or_end_node_deps:
                    self.msg_or_end_node_deps[node_id] = node_dep_info
                else:
                    self.msg_or_end_node_deps[node_id].node_dep.update(
                        node_dep_info.node_dep
                    )

    def _build_node_message_dependency(
        self, node_id: str, msg_or_end_node_dep: Dict
    ) -> None:
        """
        Build message dependency for a single node.

        :param node_id: The ID of the node
        :param msg_or_end_node_dep: Dictionary to store message dependencies
        :return: None
        """
        node_fail_branch = self._check_node_fail_branch(node_id)

        if self._should_build_message_dependency(node_id, node_fail_branch):
            # Handle special logic for iteration nodes
            if node_id.split("::")[0] == NodeType.ITERATION.value:
                if not self._iteration_chain_has_message(node_id):
                    return

            # Add current node to existing message dependencies
            for existing_dep in msg_or_end_node_dep.values():
                existing_dep.node_dep.add(node_id)

            # Create new dependency information for current node
            msg_or_end_node_dep[node_id] = MsgOrEndDepInfo(
                node_dep=set(), data_dep=set(), data_dep_path_info={}
            )

    def _check_node_fail_branch(self, node_id: str) -> bool:
        """
        Check if node has failure branch.

        :param node_id: The ID of the node to check
        :return: True if node has failure branch, False otherwise
        """
        for node in self.sparkflow_dsl.nodes:
            if node.id == node_id:
                retry_config = node.data.retryConfig
                return (
                    retry_config.get("shouldRetry", False)
                    and retry_config.get(
                        "errorStrategy", ErrorHandler.Interrupted.value
                    )
                    == ErrorHandler.FailBranch.value
                )
        return False

    def _iteration_chain_has_message(self, node_id: str) -> bool:
        """
        Check if iteration chain contains message nodes.

        :param node_id: The ID of the iteration node
        :return: True if iteration chain has message nodes, False otherwise
        """
        iteration_chain = self.chains.iteration_chains[node_id]
        for master_chain in iteration_chain.master_chains:
            for iteration_node_id in master_chain.node_id_list:
                if iteration_node_id.startswith(NodeType.MESSAGE.value):
                    return True
        return False

    def _should_build_message_dependency(
        self, node_id: str, node_fail_branch: bool
    ) -> bool:
        """
        Determine whether message dependency should be built.

        :param node_id: The ID of the node
        :param node_fail_branch: Whether the node has failure branch
        :return: True if message dependency should be built, False otherwise
        """
        node_type_prefixes = [
            NodeType.MESSAGE.value,
            NodeType.END.value,
            NodeType.IF_ELSE.value,
            NodeType.DECISION_MAKING.value,
            NodeType.QUESTION_ANSWER.value,
        ]

        return (
            any(node_id.startswith(prefix) for prefix in node_type_prefixes)
            or node_id.split("::")[0] == NodeType.ITERATION.value
            or node_fail_branch
        )
