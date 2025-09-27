import asyncio
import json
import os
from abc import abstractmethod
from asyncio import Event
from typing import Any, AsyncIterator, Dict, List, Literal, Optional, Tuple

from pydantic import BaseModel, Field
from workflow.consts.engine.chat_status import ChatStatus, SparkLLMStatus
from workflow.consts.engine.model_provider import ModelProviderEnum
from workflow.consts.engine.template import TemplateSplitType, TemplateType
from workflow.domain.entities.chat import HistoryItem
from workflow.engine.callbacks.callback_handler import ChatCallBacks
from workflow.engine.callbacks.openai_types_sse import GenerateUsage
from workflow.engine.entities.history import (
    EnableChatHistoryV2,
    History,
    ProcessArrayMethod,
)
from workflow.engine.entities.msg_or_end_dep_info import MsgOrEndDepInfo
from workflow.engine.entities.node_entities import NodeType
from workflow.engine.entities.node_running_status import NodeRunningStatus
from workflow.engine.entities.output_mode import EndNodeOutputModeEnum
from workflow.engine.entities.retry_config import RetryConfig
from workflow.engine.entities.variable_pool import ParamKey, VariablePool
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.util.frame_processor import (
    AIPaaSFrameProcessor,
    FrameProcessor,
    FrameProcessorFactory,
    UnionFrame,
)
from workflow.engine.nodes.util.prompt import (
    PromptUtils,
    TemplateUnitObj,
    process_prompt,
)
from workflow.exception.e import CustomException
from workflow.exception.errors.code_convert import CodeConvert
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.providers.llm.chat_ai import ChatAI
from workflow.infra.providers.llm.chat_ai_factory import ChatAIFactory
from workflow.infra.providers.llm.iflytek_spark.const import RespFormatEnum
from workflow.infra.providers.llm.iflytek_spark.schemas import (
    SparkAiMessage,
    StreamOutputMsg,
)
from workflow.infra.providers.llm.types import SystemUserMsg
from workflow.utils.file_util import url_to_base64


class BaseNode(BaseModel):
    """
    Base class for all workflow nodes.

    This class defines the common structure and behavior for all nodes in the workflow engine.
    It provides the foundation for node execution, configuration, and result handling.

    :param input_identifier: List of input identifiers for this node
    :param output_identifier: List of output identifiers for this node
    :param node_type: Type of the node (e.g., 'llm', 'decision', 'code')
    :param alias_name: Human-readable name for the node
    :param node_id: Unique identifier for the node
    :param retry_config: Configuration for retry mechanism
    :param stream_node_first_token: Event to track if streaming node has sent first token
    :param remarkVisible: Whether the remark is visible in UI
    :param remark: Additional remarks or notes for the node
    """

    input_identifier: List[Any]
    output_identifier: List[Any]
    node_type: str = ""
    alias_name: str = ""
    node_id: str = ""
    retry_config: RetryConfig = Field(default_factory=RetryConfig)
    stream_node_first_token: Event = Field(
        default_factory=Event
    )  # Event to track if streaming node has sent first token
    remarkVisible: bool = False
    remark: str = ""

    class Config:
        arbitrary_types_allowed = True

    @abstractmethod
    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Execute the node asynchronously.

        This method should be implemented by all subclasses to define
        the asynchronous execution logic for the specific node type.

        :param variable_pool: Pool containing variables and their values
        :param span: Tracing span for monitoring and debugging
        :param event_log_node_trace: Optional node trace logging
        :param kwargs: Additional keyword arguments including:
            - callback: Hook method for callbacks
        :return: NodeRunResult containing execution results
        """
        raise NotImplementedError

    async def put_stream_content(
        self,
        node_id: str,
        variable_pool: VariablePool,
        msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo],
        domain: str,
        content: dict,
    ) -> None:
        """
        Put request content into the streaming queue.

        This method handles the streaming output by putting content into the
        appropriate streaming queues for dependent nodes.

        :param node_id: ID of the current node
        :param variable_pool: Pool containing variables and streaming data
        :param msg_or_end_node_deps: Dependencies on message or end nodes
        :param domain: Domain/model name for the content
        :param content: Content to be streamed
        :return: None
        """
        try:
            if not variable_pool.get_stream_node_has_sent_first_token(node_id):
                # Mark that streaming node has sent first token
                variable_pool.set_stream_node_has_sent_first_token(node_id)
            if not self.stream_node_first_token.is_set():
                # Mark that streaming output first frame has been sent,
                # triggering engine to set exception branches as inactive
                self.stream_node_first_token.set()
            if not msg_or_end_node_deps:
                # No node dependencies during single node debugging
                return

            if not variable_pool.stream_data:
                return

            for msg_end_node, info in msg_or_end_node_deps.items():
                data_dep = info.data_dep
                if node_id in data_dep:
                    await variable_pool.stream_data[msg_end_node][node_id].put(
                        StreamOutputMsg(domain=domain, llm_response=content)
                    )
        except Exception as e:
            raise e

    def get_stream_done_content(self) -> dict:
        """
        Get the content indicating streaming is complete.

        This method returns a dictionary that signals the end of streaming
        for this node.

        :return: Dictionary with finish_reason set to "stop"
        """
        return {"finish_reason": ChatStatus.FINISH_REASON.value}

    def success(
        self,
        inputs: dict,
        outputs: dict,
        raw_output: Optional[str] = "",
        token_cost: Optional[GenerateUsage] = None,
    ) -> NodeRunResult:
        """
        Create a successful node execution result.

        This method creates a NodeRunResult object indicating successful
        execution of the node with the provided parameters.

        :param inputs: Input parameters for the node
        :param outputs: Output parameters from the node
        :param raw_output: Raw execution result
        :param token_cost: Token usage information for LLM nodes
        :return: NodeRunResult with SUCCEEDED status
        """
        result = NodeRunResult(
            status=WorkflowNodeExecutionStatus.SUCCEEDED,
            inputs=inputs,
            outputs=outputs,
            raw_output=raw_output if raw_output else "",
            node_id=self.node_id,
            alias_name=self.alias_name,
            node_type=self.node_type,
        )
        if token_cost:
            result.token_cost = token_cost
        return result

    def fail(self, error: Exception, code_enum: CodeEnum, span: Span) -> NodeRunResult:
        """
        Create a failed node execution result.

        This method creates a NodeRunResult object indicating failed
        execution of the node with the provided error information.

        :param error: The exception that caused the failure
        :param code_enum: Error code enumeration
        :param span: Tracing span for recording the exception
        :return: NodeRunResult with FAILED status
        """
        span.record_exception(error)
        if isinstance(error, CustomException):
            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.FAILED,
                error=error,
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )
        else:
            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.FAILED,
                error=CustomException(
                    code_enum,
                    cause_error=error,
                ),
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )


# List of node types that support branching logic
BRANCH_NODE_TYPE = [
    NodeType.IF_ELSE.value,
    NodeType.DECISION_MAKING.value,
    NodeType.QUESTION_ANSWER.value,
]


class OutputNodeFrameData(BaseModel):
    """
    Data structure for streaming output frames from output nodes.

    This class represents a single frame of streaming output data,
    containing both content and metadata about the frame.

    :param content: Main content from the model
    :param reasoning_content: Reasoning/thinking content from the model
    :param data_type: Type of data (e.g., "text", "json")
    :param is_end: Whether this is the final frame in the stream
    :param exception_occurred: Whether an exception occurred during processing
    """

    # Model content
    content: str = ""
    # Model reasoning content
    reasoning_content: str = ""
    # Data type
    data_type: str = "text"
    # Whether this is the end frame
    is_end: bool = False
    # Whether an exception occurred
    exception_occurred: bool = False


class BaseOutputNode(BaseNode):
    """
    Base class for nodes that handle streaming output data.

    This class extends BaseNode to provide specialized functionality
    for nodes that process and output streaming data, such as message
    nodes and end nodes.

    :param streamOutput: Whether this node supports streaming output
    """

    streamOutput: bool = Field(default=False)

    @abstractmethod
    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Execute the output node asynchronously.

        This method should be implemented by all subclasses to define
        the asynchronous execution logic for the specific output node type.

        :param variable_pool: Pool containing variables and their values
        :param span: Tracing span for monitoring and debugging
        :param event_log_node_trace: Optional node trace logging
        :param kwargs: Additional keyword arguments
        :return: NodeRunResult containing execution results
        """
        raise NotImplementedError

    async def await_pre_output_node_complete(
        self,
        msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo],
        node_run_status: Dict[str, NodeRunningStatus],
    ) -> bool:
        """
        Wait for preceding message nodes to complete execution.

        This method waits for all dependent message nodes to finish
        their execution before proceeding with the current node.

        :param msg_or_end_node_deps: Dependencies on message or end nodes
        :param node_run_status: Status tracking for all nodes
        :return: True if node should run, False if it should be skipped
        """
        is_run = True
        # Wait for preceding message nodes to complete execution
        for dep_msg_node in msg_or_end_node_deps[self.node_id].node_dep:
            await node_run_status[dep_msg_node].complete.wait()
            if node_run_status[self.node_id].not_run.is_set():
                # This node is logically running but actually not running
                is_run = False
                break
        return is_run

    def add_output_into_not_stream_output_cache(
        self,
        output_node_frame_data_: OutputNodeFrameData,
        not_stream_output_cache: dict[str, list[Any]],
    ) -> None:
        """
        Add output frame data to non-streaming output cache.

        This method accumulates output content and reasoning content
        into the cache for non-streaming output scenarios.

        :param output_node_frame_data_: Frame data to be cached
        :param not_stream_output_cache: Cache for non-streaming output
        :return: None
        """
        if output_node_frame_data_.content:
            not_stream_output_cache["content"].append(output_node_frame_data_.content)
        if output_node_frame_data_.reasoning_content:
            not_stream_output_cache["reasoning_content"].append(
                output_node_frame_data_.reasoning_content
            )

    async def deal_output_stream_msg(
        self,
        variable_pool: VariablePool,
        template: str,
        reasoning_template: str,
        callbacks: ChatCallBacks,
        node_run_status: Dict[str, NodeRunningStatus],
        span: Span,
    ) -> Optional[OutputNodeFrameData]:
        """
        Process streaming output for output nodes.

        This method handles the streaming output processing for output nodes,
        managing both normal content and reasoning content streams.

        :param variable_pool: Pool containing variables and streaming data
        :param template: Template for normal content processing
        :param reasoning_template: Template for reasoning content processing
        :param callbacks: Callback handlers for streaming events
        :param node_run_status: Status tracking for all nodes
        :param span: Tracing span for monitoring
        :return: The final frame of the stream, or None if not applicable
        """

        # Cache content output of LLM nodes
        llm_output_cache: Dict[str, List[OutputNodeFrameData]] = {}

        # Cache reasoning/thinking output of LLM nodes
        llm_reasoning_content: Dict[str, List[OutputNodeFrameData]] = {}

        # Track whether each LLM node has finished output
        llm_output_status: Dict[str, bool] = {}

        # Track whether an exception occurred for each LLM node;
        # if True, values should be fetched directly from the variable pool
        llm_occur_exception: Dict[str, bool] = {}

        class TemplateUnit(BaseModel):
            template: str
            template_type: TemplateType
            unit_list: list[TemplateUnitObj]

            class Config:
                arbitrary_types_allowed = True

        # Build template units for reasoning and normal content
        template_units: list[TemplateUnit] = [
            TemplateUnit(
                template=reasoning_template,
                template_type=TemplateType.REASONING,
                unit_list=[],
            ),
            TemplateUnit(
                template=template, template_type=TemplateType.NORMAL, unit_list=[]
            ),
        ]
        for template_unit in template_units:
            template_unit.unit_list = PromptUtils.get_template_unit(
                self.node_id, template_unit.template, variable_pool, span
            )

        # Initialize output caches for all referenced dependency nodes
        for template_unit in template_units:
            for unit in template_unit.unit_list:
                if unit.ref_node_info:
                    llm_output_cache[unit.ref_node_info.ref_node_id] = []
                    llm_reasoning_content[unit.ref_node_info.ref_node_id] = []

        # Cache buffers for non-streaming output frames
        not_stream_output_cache: dict[str, list] = {
            "reasoning_content": [],
            "content": [],
        }

        # Stream outputs for different template types (reasoning and normal)
        for template_unit in template_units:
            async for output_node_frame_data in self.msg_or_end_node_stream_output(
                variable_pool=variable_pool,
                template_unit_list=template_unit.unit_list,
                node_run_status=node_run_status,
                llm_output_cache=llm_output_cache,
                llm_reasoning_content=llm_reasoning_content,
                template_type=template_unit.template_type,
                llm_output_status=llm_output_status,
                llm_occur_exception=llm_occur_exception,
                span=span,
            ):
                if (
                    output_node_frame_data.is_end
                    and template_unit.template_type == TemplateType.NORMAL
                ):
                    # Only in NORMAL mode with is_end=True should this be treated as the final frame
                    if not self.streamOutput:
                        self.add_output_into_not_stream_output_cache(
                            output_node_frame_data, not_stream_output_cache
                        )
                        return OutputNodeFrameData(
                            content="".join(not_stream_output_cache["content"]),
                            reasoning_content="".join(
                                not_stream_output_cache["reasoning_content"]
                            ),
                            data_type="text",
                            is_end=True,
                        )
                    return output_node_frame_data
                else:
                    if not self.streamOutput:
                        self.add_output_into_not_stream_output_cache(
                            output_node_frame_data, not_stream_output_cache
                        )
                        continue
                    await callbacks.on_node_process(
                        code=0,
                        node_id=self.node_id,
                        alias_name=self.alias_name,
                        message=output_node_frame_data.content,
                        reasoning_content=output_node_frame_data.reasoning_content,
                    )
        return None

    def get_variable_from_vp(
        self,
        variable_pool: VariablePool,
        template_unit: TemplateUnitObj,
        span: Span,
        template_type: TemplateType = TemplateType.NORMAL,
    ) -> OutputNodeFrameData:
        """
        Retrieve variable value from variable pool and format as output frame data.

        This method processes a template unit by retrieving its value from the
        variable pool and formatting it according to the template type.

        :param variable_pool: Pool containing variables and their values
        :param template_unit: Template unit containing variable information
        :param span: Tracing span for monitoring
        :param template_type: Type of template (NORMAL or REASONING)
        :return: OutputNodeFrameData containing the processed variable value
        """
        val = process_prompt(
            node_id=self.node_id,
            key_name=template_unit.key,
            variable_pool=variable_pool,
            span=span,
        )
        val = val if isinstance(val, str) else json.dumps(val, ensure_ascii=False)
        return OutputNodeFrameData(
            content=val if template_type == TemplateType.NORMAL else "",
            reasoning_content=val if template_type == TemplateType.REASONING else "",
            data_type="text",
            is_end=template_unit.is_end,
        )

    def _is_valid_stream_dependency(
        self,
        dep_node_id: str,
        template_unit: TemplateUnitObj,
        variable_pool: VariablePool,
    ) -> bool:
        """
        Check if a dependency node supports streaming output.

        This method determines whether a dependent node can provide streaming
        output based on its node type and configuration.

        :param dep_node_id: ID of the dependent node
        :param template_unit: Template unit containing variable information
        :param variable_pool: Pool containing system parameters and node configurations
        :return: True if the dependency supports streaming, False otherwise
        """
        node_type = dep_node_id.split(":")[0]

        if node_type in [NodeType.LLM.value, NodeType.AGENT.value]:
            # LLM and Agent nodes always support streaming
            return True

        if not template_unit.ref_node_info:
            raise ValueError(f"Node {dep_node_id} has no ref node info")

        if node_type == NodeType.KNOWLEDGE_PRO.value:
            # Knowledge Pro nodes support streaming except for result variables
            return not template_unit.ref_node_info.ref_var_name.startswith("result")

        if node_type == NodeType.FLOW.value:
            # Flow nodes support streaming only in prompt mode
            flow_output_mode = variable_pool.system_params.get(
                ParamKey.FlowOutputMode, node_id=dep_node_id
            )
            return flow_output_mode == EndNodeOutputModeEnum.PROMPT_MODE.value

        return False

    async def _process_llm_output_stream(
        self,
        dep_node_id: str,
        variable_pool: VariablePool,
        template_unit: TemplateUnitObj,
        span: Span,
        llm_output_cache: Dict[str, List[OutputNodeFrameData]] = {},
        llm_reasoning_content: Dict[str, List[OutputNodeFrameData]] = {},
        template_type: TemplateType = TemplateType.NORMAL,
        llm_output_status: Dict[str, bool] = {},
        llm_occur_exception: Dict[str, bool] = {},
    ) -> AsyncIterator[OutputNodeFrameData]:
        """
        Process streaming output from LLM or related nodes.

        This method handles the streaming output processing for LLM, Agent,
        Knowledge Pro, and Flow nodes, managing frame processing and exception handling.

        :param dep_node_id: ID of the dependent node providing the stream
        :param variable_pool: Pool containing variables and node protocols
        :param template_unit: Template unit containing variable information
        :param span: Tracing span for monitoring
        :param llm_output_cache: Cache for LLM output content
        :param llm_reasoning_content: Cache for LLM reasoning content
        :param template_type: Type of template (NORMAL or REASONING)
        :param llm_output_status: Status tracking for LLM nodes
        :param llm_occur_exception: Exception tracking for LLM nodes
        :return: AsyncIterator yielding OutputNodeFrameData
        """
        frame_processor = None
        dep_node_id_prefix = dep_node_id.split(":")[0]

        if dep_node_id_prefix in [
            NodeType.AGENT.value,
            NodeType.KNOWLEDGE_PRO.value,
            NodeType.FLOW.value,
        ]:
            # Get frame processor for specialized node types
            frame_processor = FrameProcessorFactory.get_processor(dep_node_id_prefix)
        else:
            # LLM Node - get processor based on model source
            dep_node_protocol = variable_pool.get_node_protocol(dep_node_id)
            model_source = dep_node_protocol.nodeParam.get(
                "source", ModelProviderEnum.XINGHUO.value
            )
            frame_processor = FrameProcessorFactory.get_processor(model_source)

        async for data in self._deal_llm_output_stream_msg(
            template_unit=template_unit,
            dep_node_id=dep_node_id,
            variable_pool=variable_pool,
            llm_output_cache=llm_output_cache,
            llm_reasoning_content=llm_reasoning_content,
            template_type=template_type,
            llm_output_status=llm_output_status,
            frame_processor=frame_processor,
            span=span,
        ):
            if data.exception_occurred:
                # LLM encountered an interruption exception, get all values from variable pool
                llm_occur_exception[dep_node_id] = True
                data = self.get_variable_from_vp(
                    variable_pool=variable_pool,
                    template_unit=template_unit,
                    template_type=template_type,
                    span=span,
                )
            if data.is_end and not template_unit.is_end:
                data.is_end = template_unit.is_end
            yield data

    async def msg_or_end_node_stream_output(
        self,
        variable_pool: VariablePool,
        template_unit_list: list[TemplateUnitObj],
        node_run_status: Dict[str, NodeRunningStatus],
        span: Span,
        llm_output_cache: Dict[str, List[OutputNodeFrameData]] = {},
        llm_reasoning_content: Dict[str, List[OutputNodeFrameData]] = {},
        template_type: TemplateType = TemplateType.NORMAL,
        llm_output_status: Dict[str, bool] = {},
        llm_occur_exception: Dict[str, bool] = {},
    ) -> AsyncIterator[OutputNodeFrameData]:
        """
        Handle streaming output for different referenced node types.

        This method processes template units and generates streaming output
        based on the type of referenced nodes (constants, variables, LLM outputs).

        :param variable_pool: Pool containing variables and streaming data
        :param template_unit_list: List of template units to process
        :param node_run_status: Status tracking for all nodes
        :param span: Tracing span for monitoring
        :param llm_output_cache: Cache for LLM output content
        :param llm_reasoning_content: Cache for LLM reasoning content
        :param template_type: Type of template (NORMAL or REASONING)
        :param llm_output_status: Status tracking for LLM nodes
        :param llm_occur_exception: Exception tracking for LLM nodes
        :return: AsyncIterator yielding OutputNodeFrameData
        """

        for template_unit in template_unit_list:
            # Handle constant data
            if template_unit.key_type == TemplateSplitType.CONSTS.value:
                yield OutputNodeFrameData(
                    content=(
                        template_unit.value
                        if template_type == TemplateType.NORMAL
                        else ""
                    ),
                    reasoning_content=(
                        template_unit.value
                        if template_type == TemplateType.REASONING
                        else ""
                    ),
                    data_type="text",
                    is_end=template_unit.is_end,
                )

            dep_node_id = (
                template_unit.ref_node_info.ref_node_id
                if template_unit.ref_node_info
                else ""
            )

            # Handle node data
            if template_unit.key_type == TemplateSplitType.VARIABLE.value:
                if not self._is_valid_stream_dependency(
                    dep_node_id, template_unit, variable_pool
                ):
                    # If not an LLM node, get value from variable pool
                    await node_run_status[dep_node_id].complete.wait()
                    res: OutputNodeFrameData = self.get_variable_from_vp(
                        variable_pool=variable_pool,
                        template_unit=template_unit,
                        template_type=template_type,
                        span=span,
                    )
                    yield res
                    continue

                await node_run_status[dep_node_id].processing.wait()
                if node_run_status[dep_node_id].not_run.is_set():
                    # Node is logically running but actually not running
                    yield OutputNodeFrameData(is_end=template_unit.is_end)
                    continue

                llm_output_status.setdefault(dep_node_id, False)

                if dep_node_id in llm_occur_exception:
                    # Dependent streaming output node has encountered an exception, get value directly from variable pool
                    data: OutputNodeFrameData = self.get_variable_from_vp(
                        variable_pool=variable_pool,
                        template_unit=template_unit,
                        template_type=template_type,
                        span=span,
                    )
                    if data.is_end and not template_unit.is_end:
                        data.is_end = template_unit.is_end
                    yield data
                    continue

                async for data in self._process_llm_output_stream(
                    template_unit=template_unit,
                    dep_node_id=dep_node_id,
                    variable_pool=variable_pool,
                    llm_output_cache=llm_output_cache,
                    llm_reasoning_content=llm_reasoning_content,
                    template_type=template_type,
                    llm_output_status=llm_output_status,
                    llm_occur_exception=llm_occur_exception,
                    span=span,
                ):
                    yield data

            if template_unit.key_type == TemplateSplitType.LLM_JSON.value:
                # If LLM node output is in JSON format, get parsed JSON value from variable pool
                await node_run_status[dep_node_id].complete.wait()
                val = process_prompt(
                    node_id=self.node_id,
                    key_name=template_unit.key,
                    variable_pool=variable_pool,
                    span=span,
                )
                val = (
                    val if isinstance(val, str) else json.dumps(val, ensure_ascii=False)
                )
                yield OutputNodeFrameData(
                    content=val if template_type == TemplateType.NORMAL else "",
                    reasoning_content=(
                        val if template_type == TemplateType.REASONING else ""
                    ),
                    data_type="text",
                    is_end=template_unit.is_end,
                )

    async def _llm_stream_output(
        self,
        dep_node_id: str,
        output_cache: Dict[str, List[OutputNodeFrameData]],
        template_type: TemplateType,
        is_reasoning: bool,
    ) -> AsyncIterator[OutputNodeFrameData]:
        """
        Stream output from cached LLM data.

        This method processes cached LLM output data and yields it as
        streaming output frames based on the template type and reasoning mode.

        :param dep_node_id: ID of the dependent node
        :param output_cache: Cache containing LLM output data
        :param template_type: Type of template (NORMAL or REASONING)
        :param is_reasoning: Whether this is reasoning content
        :return: AsyncIterator yielding OutputNodeFrameData
        """
        for _, data in enumerate(output_cache[dep_node_id]):
            if template_type == TemplateType.REASONING:
                yield OutputNodeFrameData(
                    reasoning_content=(
                        data.reasoning_content if is_reasoning else data.content
                    ),
                    data_type="text",
                    is_end=data.is_end,
                )
            else:
                yield OutputNodeFrameData(
                    content=(data.reasoning_content if is_reasoning else data.content),
                    data_type="text",
                    is_end=data.is_end,
                )

    async def _yield_output(
        self,
        dep_node_id: str,
        status: int,
        content: str,
        reasoning_content: str,
        llm_output_cache: Dict[str, List[OutputNodeFrameData]] = {},
        llm_reasoning_content: Dict[str, List[OutputNodeFrameData]] = {},
        template_type: TemplateType = TemplateType.NORMAL,
        is_reasoning: bool = False,
    ) -> AsyncIterator[OutputNodeFrameData]:
        """
        Yield output data and manage caching for LLM responses.

        This method processes LLM output content and reasoning content,
        managing caching and yielding appropriate output frames.

        :param dep_node_id: ID of the dependent node
        :param status: Status code from LLM response
        :param content: Main content from LLM
        :param reasoning_content: Reasoning content from LLM
        :param llm_output_cache: Cache for LLM output content
        :param llm_reasoning_content: Cache for LLM reasoning content
        :param template_type: Type of template (NORMAL or REASONING)
        :param is_reasoning: Whether this is reasoning content
        :return: AsyncIterator yielding OutputNodeFrameData
        """
        # If outputting reasoning chain variables
        if reasoning_content:
            llm_reasoning_content[dep_node_id].append(
                OutputNodeFrameData(reasoning_content=reasoning_content)
            )
        else:
            # Reasoning chain output completed, return directly
            llm_reasoning_content[dep_node_id].append(OutputNodeFrameData(is_end=True))
            llm_output_cache[dep_node_id].append(
                OutputNodeFrameData(
                    content=content, is_end=(status == SparkLLMStatus.END.value)
                )
            )
            if is_reasoning:
                # If outputting reasoning process, can exit after output
                return
        if is_reasoning:
            # Currently outputting reasoning content
            yield OutputNodeFrameData(
                reasoning_content=(
                    reasoning_content if template_type == TemplateType.REASONING else ""
                ),
                content=(
                    reasoning_content if template_type == TemplateType.NORMAL else ""
                ),
                data_type="text",
            )
        else:
            # Currently outputting LLM output content
            if content:
                yield OutputNodeFrameData(
                    reasoning_content=(
                        content if template_type == TemplateType.REASONING else ""
                    ),
                    content=(content if template_type == TemplateType.NORMAL else ""),
                    data_type="text",
                    is_end=(status == SparkLLMStatus.END.value),
                )

    async def _process_queue_output(
        self,
        dep_node_id: str,
        variable_pool: VariablePool,
        span: Span,
        llm_output_cache: Dict[str, List[OutputNodeFrameData]] = {},
        llm_reasoning_content: Dict[str, List[OutputNodeFrameData]] = {},
        template_type: TemplateType = TemplateType.NORMAL,
        llm_output_status: Dict[str, bool] = {},
        frame_processor: FrameProcessor = AIPaaSFrameProcessor(),
        is_reasoning: bool = False,
    ) -> AsyncIterator[OutputNodeFrameData]:
        """
        Process streaming output from the message queue.

        This method continuously processes messages from the streaming queue,
        handling frame processing, error handling, and output generation.

        :param dep_node_id: ID of the dependent node
        :param variable_pool: Pool containing streaming data queues
        :param span: Tracing span for monitoring
        :param llm_output_cache: Cache for LLM output content
        :param llm_reasoning_content: Cache for LLM reasoning content
        :param template_type: Type of template (NORMAL or REASONING)
        :param llm_output_status: Status tracking for LLM nodes
        :param frame_processor: Processor for handling frame data
        :param is_reasoning: Whether this is reasoning content
        :return: AsyncIterator yielding OutputNodeFrameData
        """
        queue = variable_pool.stream_data[self.node_id][dep_node_id]
        while True:
            try:
                # If the LLM node has already finished output, break directly
                # Scenario: User limited output token count, reasoning ended early, avoid waiting for content
                if llm_output_status[dep_node_id]:
                    break
                msg: StreamOutputMsg = await asyncio.wait_for(queue.get(), timeout=120)
                llm_response = msg.llm_response
                exception_occurred = msg.exception_occurred
                span.add_info_events(
                    {"recv": json.dumps(llm_response, ensure_ascii=False)}
                )
                frame: UnionFrame = frame_processor.process_frame(llm_response)
                code = frame.code
                status = int(frame.status)
                text = frame.text

                llm_output_status[dep_node_id] = (
                    True
                    if status == SparkLLMStatus.END.value
                    else llm_output_status[dep_node_id]
                )

                content = text.get("content", "")
                reasoning_content = text.get("reasoning_content", "")

                if code != 0:
                    # TODO: Handle error reporting
                    llm_output_status[dep_node_id] = True
                    if exception_occurred:
                        # When exception_occurred=True, an exception interruption occurred,
                        # engine adds an end frame with code=-1, return to upper layer
                        # so upper layer knows to get values from variable pool
                        yield OutputNodeFrameData(
                            reasoning_content="",
                            content="",
                            data_type="text",
                            is_end=True,
                            exception_occurred=True,
                        )
                    break
                async for data in self._yield_output(
                    dep_node_id,
                    status,
                    content,
                    reasoning_content,
                    llm_output_cache,
                    llm_reasoning_content,
                    template_type,
                    is_reasoning,
                ):
                    yield data
                if status == SparkLLMStatus.END.value or (
                    template_type == TemplateType.REASONING
                    and reasoning_content == ""
                    and is_reasoning
                ):
                    break
            except asyncio.TimeoutError:
                # TODO: Handle timeout exception
                break

    async def _deal_llm_output_stream_msg(
        self,
        template_unit: TemplateUnitObj,
        dep_node_id: str,
        variable_pool: VariablePool,
        span: Span,
        llm_output_cache: Dict[str, List[OutputNodeFrameData]] = {},
        llm_reasoning_content: Dict[str, List[OutputNodeFrameData]] = {},
        template_type: TemplateType = TemplateType.NORMAL,
        llm_output_status: Dict[str, bool] = {},
        frame_processor: FrameProcessor = AIPaaSFrameProcessor(),
    ) -> AsyncIterator[OutputNodeFrameData]:
        """
        Handle streaming output from LLM nodes.

        This method manages the streaming output processing for LLM nodes,
        including cache management and queue processing.

        :param template_unit: Template unit containing variable information
        :param dep_node_id: ID of the dependent LLM node
        :param variable_pool: Pool containing variables and streaming data
        :param span: Tracing span for monitoring
        :param llm_output_cache: Cache for LLM output content
        :param llm_reasoning_content: Cache for LLM reasoning content
        :param template_type: Type of template (NORMAL or REASONING)
        :param llm_output_status: Status tracking for LLM nodes
        :param frame_processor: Processor for handling frame data
        :return: AsyncIterator yielding OutputNodeFrameData
        """

        dep_var_name = variable_pool.get_variable_ref_node_id(
            self.node_id, template_unit.key, span=span
        ).ref_var_name
        is_reasoning = dep_var_name == "REASONING_CONTENT"

        if not is_reasoning:
            # If it's an LLM node, get values from message queue or from llm_output_cache
            if dep_node_id in llm_output_cache and llm_output_cache[dep_node_id]:
                async for data in self._llm_stream_output(
                    dep_node_id, llm_output_cache, template_type, is_reasoning
                ):
                    yield data
                # Ensure the last frame from model is obtained, otherwise get from queue later
                if llm_output_cache[dep_node_id][-1].is_end:
                    return
        else:
            if (
                dep_node_id in llm_reasoning_content
                and llm_reasoning_content[dep_node_id]
            ):
                async for data in self._llm_stream_output(
                    dep_node_id, llm_reasoning_content, template_type, is_reasoning
                ):
                    yield data
                # Ensure the last frame from model is obtained, otherwise get from queue later
                if llm_reasoning_content[dep_node_id][-1].is_end:
                    return
        async for data in self._process_queue_output(
            dep_node_id,
            variable_pool,
            span,
            llm_output_cache,
            llm_reasoning_content,
            template_type,
            llm_output_status,
            frame_processor,
            is_reasoning,
        ):
            yield data


class BaseLLMNode(BaseNode):
    """
    Base class for Large Language Model (LLM) nodes.

    This class provides the foundation for all LLM-based nodes in the workflow,
    including configuration, chat AI initialization, and message processing.

    :param model: Model identifier
    :param url: API endpoint URL
    :param domain: Model domain/version
    :param temperature: Sampling temperature for generation
    :param appId: Application ID for authentication
    :param apiKey: API key for authentication
    :param apiSecret: API secret for authentication
    :param maxTokens: Maximum number of tokens to generate
    :param uid: User identifier
    :param template: Prompt template
    :param systemTemplate: System prompt template
    :param topK: Top-K sampling parameter
    :param patch_id: List of patch IDs
    :param respFormat: Response format (0=text, 1=json)
    :param enableChatHistory: Whether to enable chat history
    :param enableChatHistoryV2: Chat history v2 configuration
    :param re_match_pattern: Regex pattern for matching responses
    :param source: Model provider source
    :param searchDisable: Whether to disable search functionality
    :param extraParams: Additional parameters
    :param chat_ai: Chat AI instance
    """

    model: str = Field(...)
    domain: str = Field(...)
    appId: str = Field(...)
    apiKey: str = Field(...)
    apiSecret: str = Field(...)

    url: str = Field(default="")
    temperature: float = Field(gt=0, le=1, default=1.0)
    maxTokens: int = Field(gt=0, default=2048)
    uid: str = Field(default="")
    template: str = Field(default="")
    systemTemplate: str = Field(default="")
    topK: int = Field(default=3)
    patch_id: list = Field(default_factory=list)
    respFormat: Literal[0, 1, 2] = 0
    enableChatHistory: bool = Field(default=False)
    enableChatHistoryV2: EnableChatHistoryV2 = Field(
        default_factory=EnableChatHistoryV2
    )
    re_match_pattern: str = Field(default=r"```(json)?(.*)```")
    source: str = Field(default=ModelProviderEnum.XINGHUO.value)
    searchDisable: bool = Field(default=True)
    extraParams: dict = Field(default_factory=dict)

    def _get_chat_ai(self) -> ChatAI:
        """
        Get or create the ChatAI instance for this LLM node.

        This method initializes the ChatAI instance using the ChatAIFactory
        with the node's configuration parameters.

        :return: ChatAI instance configured for this node
        """

        return ChatAIFactory.get_chat_ai(
            model_source=(
                ModelProviderEnum.XINGHUO.value
                if not hasattr(self, "source")
                else self.source
            ),
            model_url=self.url,
            model_name=self.domain,
            spark_version="",
            temperature=self.temperature if hasattr(self, "temperature") else None,
            app_id=self.appId,
            api_key=self.apiKey,
            api_secret=self.apiSecret,
            max_tokens=self.maxTokens if hasattr(self, "maxTokens") else None,
            top_k=self.topK if hasattr(self, "topK") else None,
            patch_id=self.patch_id,
            uid=self.uid,
            stream_node_first_token=self.stream_node_first_token,
        )

    def _process_history(
        self,
        user_input: str,
        span_context: Span,
        history: list[SparkAiMessage] | None = [],
        history_v2: History | None = None,
        system_input: str = "",
    ) -> SystemUserMsg:
        """
        Process chat history and prepare system/user messages.

        This method processes the chat history, system input, and user input
        to prepare the messages for LLM interaction.

        :param user_input: User's input message
        :param span_context: Tracing span for logging
        :param history: Legacy chat history format
        :param history_v2: New chat history format with token management
        :param system_input: System prompt input
        :return: SystemUserMsg containing processed messages
        """
        system_msg = None
        processed_history: list[dict[str, Any]] | list[HistoryItem] = []
        if history:
            processed_history = [h.dict() for h in history]
            span_context.add_info_events(
                {"history": json.dumps(processed_history, ensure_ascii=False)}
            )

        if system_input:
            system_msg = {"role": "system", "content": system_input}
            span_context.add_info_events(
                {"system_input": json.dumps(system_msg, ensure_ascii=False)}
            )

        user_msg = {"role": "user", "content": user_input}
        span_context.add_info_events({"user_input": str(user_msg)})

        if history_v2:
            # Subtract system_input and user_input token usage
            system_input_usage = 0
            if system_msg:
                system_input_usage = ProcessArrayMethod.calculate_message_token(
                    system_msg
                )
            user_inpt_usage = ProcessArrayMethod.calculate_message_token(user_msg)
            max_token = history_v2.max_token - system_input_usage - user_inpt_usage
            rounds = history_v2.rounds
            # Process historical messages based on new token count
            processed_history = history_v2.process_history(
                data=history_v2.origin_history, max_token=max_token, rounds=rounds
            )
        return SystemUserMsg(
            system_msg=system_msg,
            user_msg=user_msg,
            processed_history=processed_history,
        )

    def _assemble_messages(
        self,
        span_context: Span,
        system_user_msg: SystemUserMsg,
        history_v2: History | None = None,
        image_url: str = "",
    ) -> list:
        """
        Assemble the messages data for the request.

        This method combines system messages, chat history, user messages,
        and image content into a properly formatted message list.

        :param span_context: Tracing span for logging
        :param system_user_msg: System and user message data
        :param history_v2: Chat history with token management
        :param image_url: URL of image to include in the message
        :return: List of formatted messages for LLM request
        """
        user_message: list = []
        processed_history = system_user_msg.processed_history
        user_msg = system_user_msg.user_msg
        system_msg = system_user_msg.system_msg
        image_msg = None
        payload_comp_history: list[HistoryItem] = []
        if history_v2:
            payload_comp_history = processed_history.copy()
            # Handle images in history
            if payload_comp_history and payload_comp_history[0].content_type == "image":
                if self.source == ModelProviderEnum.OPENAI.value:
                    payload_comp_history.pop(0)
                if self.source == ModelProviderEnum.XINGHUO.value:
                    image_models = os.getenv(
                        "SPARK_IMAGE_MODEL_DOMAIN", "image,imagev3"
                    ).split(",")
                    if self.domain in image_models:
                        if not image_url:
                            image_url = payload_comp_history[0].content
                        payload_comp_history.pop(0)
        image_base64 = None
        # If it's an image understanding model, reserve the first position in array for image
        if image_url:
            image_base64 = url_to_base64(image_url)
            image_msg = {
                "role": "user",
                "content": image_base64,
                "content_type": "image",
            }
            span_context.add_info_events({"image": str(image_url)})
        # Don't upload base64
        if image_msg:
            span_context.add_info_events(
                {"user_message": json.dumps(user_message[1:], ensure_ascii=False)}
            )
        else:
            span_context.add_info_events(
                {"user_message": json.dumps(user_message, ensure_ascii=False)}
            )
        user_message.extend(
            filter(None, [image_msg, system_msg, *payload_comp_history, user_msg])
        )
        return user_message

    async def _chat_with_llm(
        self,
        flow_id: str,
        variable_pool: VariablePool,
        span: Span,
        msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo] | None = None,
        history_chat: list[SparkAiMessage] | None = None,
        history_v2: History | None = None,
        prompt_template: str = "",
        system_prompt_template: str = "",
        image_url: str = "",
        stream: bool = False,
        event_log_node_trace: NodeLog | None = None,
    ) -> Tuple[dict, str, str, list]:
        """
        Chat with the LLM and process the response.

        This method handles the complete LLM interaction flow, including
        message preparation, API calls, response processing, and streaming.

        :param flow_id: Unique identifier for the workflow flow
        :param variable_pool: Pool containing variables and streaming data
        :param span: Tracing span for monitoring
        :param msg_or_end_node_deps: Dependencies on message or end nodes
        :param history_chat: Legacy chat history
        :param history_v2: New chat history with token management
        :param prompt_template: Template for user prompt
        :param system_prompt_template: Template for system prompt
        :param image_url: URL of image to include
        :param stream: Whether to enable streaming mode
        :param event_log_node_trace: Node trace logging
        :return: Tuple containing (token_usage, response_text, reasoning_content, processed_history)
        """
        chat_ai = self._get_chat_ai()
        system_user_msg = self._process_history(
            user_input=prompt_template,
            history=history_chat,
            history_v2=history_v2,
            system_input=system_prompt_template,
            span_context=span,
        )
        user_message = self._assemble_messages(
            system_user_msg=system_user_msg,
            history_v2=history_v2,
            image_url=image_url,
            span_context=span,
        )
        texts = []
        reasoning_contents = []
        think_contents = None
        token_usage = {}
        processed_history = system_user_msg.processed_history
        try:
            async for llm_response in chat_ai.achat(
                user_message=user_message,
                event_log_node_trace=event_log_node_trace,
                span=span,
                flow_id=flow_id,
                extra_params=self.extraParams,
                timeout=(
                    self.retry_config.timeout
                    if self.retry_config.should_retry
                    else None
                ),
                search_disable=self.searchDisable,
            ):
                msg = llm_response.msg
                code, status, content, reasoning_content, token_usage = (
                    self._get_chat_ai().decode_message(msg)
                )
                if code == 0:
                    if reasoning_content:
                        reasoning_contents.append(reasoning_content)
                    if stream and self.respFormat != RespFormatEnum.JSON.value:
                        await self.put_llm_content(
                            node_id=self.node_id,
                            model_name=self.domain,
                            variable_pool=variable_pool,
                            msg_or_end_node_deps=msg_or_end_node_deps or {},
                            llm_content=msg,
                        )
                    texts.append(content)
                    if status in [
                        SparkLLMStatus.END.value,
                        ChatStatus.FINISH_REASON.value,
                    ]:
                        token_usage = token_usage
                        break
                    if (
                        self.source == ModelProviderEnum.OPENAI.value
                        and status
                        and status
                        not in [
                            SparkLLMStatus.END.value,
                            ChatStatus.FINISH_REASON.value,
                        ]
                    ):
                        # Exception case: finish_reason has value but not "stop", report the issue
                        # For example, openai-gpt-4o gives "length" when max_token is very small
                        raise CustomException(err_code=CodeEnum.OPEN_AI_REQUEST_ERROR)
                else:
                    raise CustomException(
                        err_code=CodeConvert.sparkCode(code),
                        cause_error=json.dumps(msg, ensure_ascii=False),
                    )
            if texts:
                res = "".join(texts)
                span.add_info_events({"spark_llm_chat_result": "".join(texts)})
                think_contents = "".join(reasoning_contents)
                span.add_info_events(
                    {"spark_llm_reasoning_content": "".join(think_contents)}
                )
                return token_usage, res, think_contents, processed_history
            else:
                span.add_error_event("result is null")
                raise CustomException(
                    err_code=CodeEnum.SPARK_REQUEST_ERROR,
                    err_msg="LLM returned empty result",
                    cause_error="LLM returned empty result",
                )
        except Exception as e:
            raise e

    async def put_llm_content(
        self,
        node_id: str,
        model_name: str,
        variable_pool: VariablePool,
        msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo],
        llm_content: dict,
    ) -> None:
        """
        Put LLM response content into the streaming queue.

        This method handles the streaming output by putting LLM response
        content into the appropriate streaming queues for dependent nodes.

        :param node_id: ID of the current node
        :param model_name: Name of the model that generated the content
        :param variable_pool: Pool containing variables and streaming data
        :param msg_or_end_node_deps: Dependencies on message or end nodes
        :param llm_content: LLM response content to be streamed
        :return: None
        """
        try:
            if not variable_pool.get_stream_node_has_sent_first_token(node_id):
                # As long as put_llm_content method is executed, it proves LLM has sent first frame,
                # so set has_sent_first_token to True
                variable_pool.set_stream_node_has_sent_first_token(node_id)
            if not self.stream_node_first_token.is_set():
                self.stream_node_first_token.set()  # Mark streaming output first frame has been sent, trigger engine to set exception branches as inactive
            if not msg_or_end_node_deps:
                # No node dependencies during single node debugging
                return

            if not variable_pool.stream_data:
                return

            for msg_end_node, info in msg_or_end_node_deps.items():
                data_dep = info.data_dep
                if node_id in data_dep:
                    await variable_pool.stream_data[msg_end_node][node_id].put(
                        StreamOutputMsg(domain=model_name, llm_response=llm_content)
                    )
        except Exception as e:
            raise e
