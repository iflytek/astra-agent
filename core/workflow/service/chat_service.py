import asyncio
import copy
import json
import time
from asyncio import Queue
from datetime import datetime
from typing import Any, AsyncGenerator, AsyncIterator, Dict, List, Optional, Tuple, cast

from loguru import logger

from workflow.cache.engine import get_engine, set_engine
from workflow.cache.event_registry import Event, EventRegistry
from workflow.consts.app_audit import AppAuditPolicy
from workflow.consts.engine.chat_status import ChatStatus
from workflow.consts.engine.model_provider import ModelProviderEnum
from workflow.domain.entities.chat import ChatVo
from workflow.domain.entities.response import Streaming
from workflow.engine.callbacks.callback_handler import (
    ChatCallBackConsumer,
    ChatCallBacks,
    StructuredConsumer,
)
from workflow.engine.callbacks.openai_types_sse import LLMGenerate, WorkflowStep
from workflow.engine.dsl_engine import WorkflowEngine, WorkflowEngineFactory
from workflow.engine.entities.file import File
from workflow.engine.entities.msg_or_end_dep_info import MsgOrEndDepInfo
from workflow.engine.entities.node_entities import NodeType
from workflow.engine.entities.variable_pool import ParamKey, VariablePool
from workflow.engine.entities.workflow_dsl import WorkflowDSL
from workflow.engine.nodes.entities.node_run_result import NodeRunResult
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.workflow_log import WorkflowLog
from workflow.extensions.otlp.metric.meter import Meter
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.audit_system.audit_api.base import ContextList
from workflow.infra.audit_system.audit_api.iflytek.ifly_audit_api import IFlyAuditAPI
from workflow.infra.audit_system.base import FrameAuditResult
from workflow.infra.audit_system.strategy.base_strategy import AuditStrategy
from workflow.infra.audit_system.strategy.text_strategy import TextAuditStrategy
from workflow.service import audit_service
from workflow.service.flow_service import set_flow_node_output_mode
from workflow.service.history_service import get_history
from workflow.service.ops_service import kafka_report


async def event_stream(
    app_alias_id: str,
    event_id: str,
    workflow_dsl: Dict,
    workflow_dsl_update_time: datetime,
    chat_vo: ChatVo,
    is_release: bool,
    app_audit_policy: AppAuditPolicy,
    span: Span,
) -> AsyncIterator[str]:
    """
    Event stream processing function for handling chat requests and generating
    streaming responses.

    This function orchestrates the workflow execution process,including engine
    initialization, callback setup, and streaming response generation.

    :param app_alias_id: Application alias ID for identification
    :param event_id: Unique event identifier for tracking
    :param workflow_dsl: Workflow DSL definition containing node configurations
    :param workflow_dsl_update_time: Timestamp of workflow DSL last update
    :param chat_vo: Chat value object containing user input and configuration
    :param is_release: Whether running in production release environment
    :param app_audit_policy: Application audit policy for content moderation
    :param span: Distributed tracing span for monitoring and debugging
    :return: AsyncIterator yielding streaming response strings
    """
    response_queue: Queue = Queue()

    task = asyncio.create_task(
        _run(
            app_alias_id,
            event_id,
            workflow_dsl,
            workflow_dsl_update_time,
            chat_vo,
            is_release,
            app_audit_policy,
            response_queue,
            span,
        )
    )

    def _handle_task_result(t: asyncio.Task) -> None:
        try:
            t.result()
        except Exception:
            logger.exception("event_stream background task failed")

    task.add_done_callback(_handle_task_result)

    return _chat_response_stream(
        response_queue,
        chat_vo.flow_id,
        app_audit_policy,
        event_id,
        chat_vo.stream,
        is_release,
        span,
    )


def _init_workflow_trace(
    app_alias_id: str, chat_vo: ChatVo, is_release: bool, span_context: Span
) -> WorkflowLog:
    """
    Initialize workflow trace logging for monitoring and debugging.

    :param app_alias_id: Application alias ID for identification
    :param chat_vo: Chat value object containing user input and configuration
    :param is_release: Whether running in production release environment
    :param span_context: Distributed tracing span context
    :return: Initialized WorkflowLog instance
    """
    wl = WorkflowLog(
        flow_id=chat_vo.flow_id,
        sid=span_context.sid,
        app_id=app_alias_id,
        uid=chat_vo.uid,
        caller=chat_vo.ext.get("caller", "workflow"),
        bot_id=chat_vo.ext.get("bot_id", ""),
        chat_id=chat_vo.chat_id,
        log_caller="chat_sse" if is_release else "chat_sse_debug",
    )
    wl.add_srv(key="workflow_version", value=chat_vo.version)
    wl.add_q(json.dumps(chat_vo.parameters, ensure_ascii=False))
    return wl


async def _get_or_build_workflow_engine(
    is_release: bool,
    chat_vo: ChatVo,
    app_alias_id: str,
    workflow_dsl: Dict,
    workflow_dsl_update_time: datetime,
    span_context: Span,
) -> WorkflowEngine:
    """
    Get or build workflow engine with caching mechanism.

    This function attempts to retrieve a cached workflow engine first. If no valid
    cached engine exists or the cache is outdated, it builds a new engine from the DSL.

    :param is_release: Whether running in production release environment
    :param chat_vo: Chat value object containing flow configuration
    :param app_alias_id: Application alias ID for cache key generation
    :param workflow_dsl: Workflow DSL definition
    :param workflow_dsl_update_time: Timestamp of workflow DSL last update
    :param span_context: Distributed tracing span context
    :return: WorkflowEngine instance ready for execution
    """

    sparkflow_engine: WorkflowEngine
    need_rebuild = True

    # Attempt to retrieve engine from cache
    sparkflow_engine_cache_obj = get_engine(
        is_release, chat_vo.flow_id, chat_vo.version, app_alias_id
    )

    if sparkflow_engine_cache_obj:
        engine_cache_entity, build_timestamp = WorkflowEngine.loads(
            sparkflow_engine_cache_obj, span_context
        )
        if (
            engine_cache_entity
            and int(workflow_dsl_update_time.timestamp() * 1000) < build_timestamp
        ):
            sparkflow_engine = engine_cache_entity
            need_rebuild = False
            span_context.add_info_event(
                f"Retrieved Workflow engine from cache, "
                f"DSL update time: {workflow_dsl_update_time}, "
                f"engine build time: {build_timestamp}"
            )

    # Rebuild engine if cache miss or outdated
    if need_rebuild:
        start_time = time.time() * 1000
        sparkflow_engine = WorkflowEngineFactory.create_engine(
            WorkflowDSL.parse_obj(workflow_dsl.get("data", {})), span_context
        )
        span_context.add_info_event("Engine not found in cache, rebuilding from DSL")

        set_flow_node_output_mode(
            sparkflow_engine.engine_ctx.variable_pool,
            sparkflow_engine.engine_ctx.built_nodes,
            app_alias_id,
            span_context,
        )
        sparkflow_engine.engine_ctx.variable_pool.system_params.set(
            ParamKey.IsRelease, is_release
        )
        set_engine(
            is_release,
            chat_vo.flow_id,
            chat_vo.version,
            app_alias_id,
            sparkflow_engine,
            span_context,
        )

        span_context.add_info_events(
            {"rebuild_sparkflow_engine_cache_obj": f"{time.time() * 1000 - start_time}"}
        )

    return sparkflow_engine


async def _init_callbacks_and_consumers(
    sparkflow_engine: WorkflowEngine,
    response_queue: asyncio.Queue,
    need_order_stream_result_q: asyncio.Queue,
    support_stream_node_id_queue: asyncio.Queue,
    structured_data: Dict,
    span_context: Span,
    event_id: str,
    flow_id: str,
) -> Tuple[ChatCallBacks, List[asyncio.Task]]:
    """
    Initialize callback functions and consumer tasks for workflow execution.

    This function sets up the callback system and starts background consumer tasks
    to handle streaming responses and structured data processing.

    :param sparkflow_engine: Workflow engine instance
    :param response_queue: Queue for streaming response data
    :param need_order_stream_result_q: Queue for ordered stream results
    :param support_stream_node_id_queue: Queue for stream-supporting node IDs
    :param structured_data: Dictionary for structured data storage
    :param span_context: Distributed tracing span context
    :param event_id: Unique event identifier
    :param flow_id: Workflow identifier
    :return: Tuple of (ChatCallBacks, List of consumer tasks)
    """
    # Initialize callback functions
    callbacks = ChatCallBacks(
        sid=span_context.sid,
        stream_queue=response_queue,
        end_node_output_mode=sparkflow_engine.end_node_output_mode,
        support_stream_node_ids=sparkflow_engine.support_stream_node_ids,
        need_order_stream_result_q=need_order_stream_result_q,
        chains=sparkflow_engine.engine_ctx.chains,
        event_id=event_id,
        flow_id=flow_id,
    )

    # Initialize callback consumer
    callback_consumer = ChatCallBackConsumer(
        need_order_stream_result_q=need_order_stream_result_q,
        support_stream_node_id_queue=support_stream_node_id_queue,
        structured_data=structured_data,
    )

    # Initialize structured output consumer
    structured_consumer = StructuredConsumer(
        support_stream_node_id_queue=support_stream_node_id_queue,
        structured_data=structured_data,
        stream_queue=response_queue,
        support_stream_node_id_set=callback_consumer.support_stream_node_id_set,
    )

    # Start consumer tasks
    consumer_tasks = [
        asyncio.create_task(callback_consumer.consume()),
        asyncio.create_task(structured_consumer.consume()),
    ]

    return callbacks, consumer_tasks


async def _validate_file_inputs(
    workflow_dsl: Dict, chat_vo: ChatVo, span_context: Span
) -> None:
    """
    Validate file input parameters according to workflow DSL configuration.

    This function checks if required file parameters are provided and validates
    file types and formats according to the workflow definition.

    :param workflow_dsl: Workflow DSL definition containing
                         file parameter specifications
    :param chat_vo: Chat value object containing user input parameters
    :param span_context: Distributed tracing span context
    :return: None
    :raises CustomException: When file validation fails or
                             required parameters are missing
    """
    file_info_list, has_file = File.has_file_in_dsl(workflow_dsl, span_context)
    if not has_file:
        return

    for file_info in file_info_list:
        file_var_name = file_info.file_var_name
        file_var_type = file_info.file_var_type
        is_required = file_info.is_required

        # Check required parameters
        if file_var_name not in chat_vo.parameters:
            if is_required:
                raise CustomException(
                    err_code=CodeEnum.FILE_VARIABLE_PROTOCOL_ERROR,
                    err_msg=f"Error: {file_var_name} is a required parameter",
                )
            continue

        param_value = chat_vo.parameters[file_var_name]

        # Check empty values (skip if not required)
        if not param_value and not is_required:
            continue

        # Validate files based on type
        if file_var_type == "string":
            File.check_file_var_isvalid(
                param_value, file_info.allowed_file_type, span_context
            )
        elif file_var_type == "array":
            for input_file in param_value:
                File.check_file_var_isvalid(
                    input_file, file_info.allowed_file_type, span_context
                )
        else:
            span_context.add_error_event(
                f"File variable protocol error, invalid type: {file_var_type}"
            )
            raise CustomException(err_code=CodeEnum.FILE_VARIABLE_PROTOCOL_ERROR)


async def _get_chat_history(
    sparkflow_engine: WorkflowEngine, chat_vo: ChatVo, span_context: Span
) -> Any:
    """
    Retrieve chat history for nodes that require historical context.

    This function identifies nodes that need chat history (LLM and Decision nodes
    with enableChatHistory flag) and fetches relevant historical data.

    :param sparkflow_engine: Workflow engine instance
    :param chat_vo: Chat value object containing user information
    :param span_context: Distributed tracing span context
    :return: List of historical chat records or empty list
    """
    uid = chat_vo.uid
    history = []

    # Check nodes that require historical records
    nodes_need_history = [
        node
        for node in sparkflow_engine.workflow_dsl.nodes
        if node.id.startswith((NodeType.LLM.value, NodeType.DECISION_MAKING.value))
        and node.data.nodeParam.get("enableChatHistory", False)
    ]

    if nodes_need_history:
        start_time = time.time() * 1000
        history = get_history(
            flow_id=chat_vo.flow_id,
            uid=uid,
            node_max_token=sparkflow_engine.node_max_token,
        )
        span_context.add_info_events(
            {"get_node_history_from_database": f"{time.time() * 1000 - start_time}"}
        )

    return history


async def _perform_input_audit(chat_vo: ChatVo, span: Span) -> None:
    """
    Perform input content audit for content moderation.

    This function processes user input through the audit system to ensure
    content compliance with platform policies.

    :param chat_vo: Chat value object containing user input and history
    :param span: Distributed tracing span for monitoring
    :return: None
    :raises CustomException: When content audit fails
    """
    context_list = [
        ContextList(role=history.role, content=history.content)
        for history in chat_vo.history
    ]

    await audit_service.input_audit(
        audit_service.splice_input_content(chat_vo.parameters),
        span,
        context_list,
    )


async def _process_and_report_result(
    result: NodeRunResult,
    workflow_trace: WorkflowLog,
    span_context: Span,
    consumer_tasks: List[asyncio.Task],
) -> None:
    """
    Process workflow execution results and report trace information.

    This function processes the final workflow result, logs trace information,
    and ensures all consumer tasks complete properly.

    :param result: Node execution result containing outputs and inputs
    :param workflow_trace: Workflow trace logger for recording execution details
    :param span_context: Distributed tracing span context
    :param consumer_tasks: List of background consumer tasks to wait for
    :return: None
    """
    # Process results
    outputs = (
        {"output": result.node_answer_content}
        if result.node_answer_content
        else result.outputs
    )
    outputs_assemble = {**outputs, **(result.inputs or {})}

    # Record trace information
    workflow_trace.add_a(json.dumps(outputs_assemble, ensure_ascii=False))
    span_context.add_info_events({"workflow_output": result.model_dump_json()})

    # Wait for consumer tasks to complete
    for task in consumer_tasks:
        if not task.done():
            await task


async def _cleanup_resources(consumer_tasks: List[asyncio.Task]) -> None:
    """
    Clean up resources by cancelling consumer tasks.

    This function ensures proper cleanup of background tasks to prevent
    resource leaks and hanging processes.

    :param consumer_tasks: List of consumer tasks to cancel
    :return: None
    """
    for task in consumer_tasks:
        if not task.done():
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass


async def _run(
    app_alias_id: str,
    event_id: str,
    workflow_dsl: Dict,
    workflow_dsl_update_time: datetime,
    chat_vo: ChatVo,
    is_release: bool,
    app_audit_policy: AppAuditPolicy,
    response_queue: Queue,
    span: Span,
) -> None:
    """
    Process chat request and execute workflow.

    This is the main workflow execution function that orchestrates the entire
    process from engine initialization to result processing and cleanup.

    :param app_alias_id: Application alias ID for identification
    :param event_id: Unique event identifier for tracking
    :param workflow_dsl: Workflow DSL definition containing node configurations
    :param workflow_dsl_update_time: Timestamp of workflow DSL last update
    :param chat_vo: Chat value object containing user input and configuration
    :param is_release: Whether running in production release environment
    :param response_queue: Response queue for streaming output results
    :param app_audit_policy: Application audit policy for content moderation
    :param span: Distributed tracing span for monitoring
    :return: None
    """
    func_name = "sse_chat_open" if is_release else "sse_chat_debug"
    m = Meter(app_id=app_alias_id, func=func_name)
    m.set_label("flow_id", chat_vo.flow_id)

    with span.start(
        attributes={"flow_id": chat_vo.flow_id},
    ) as span_context:
        span.add_info_event(f"user input: {chat_vo.json()}")
        span.add_info_event(
            f"spark dsl: {json.dumps(workflow_dsl, ensure_ascii=False)}"
        )

        workflow_trace = _init_workflow_trace(
            app_alias_id, chat_vo, is_release, span_context
        )
        code = 0
        error_message = ""
        try:

            # Get or build workflow engine
            src_sparkflow_engine = await _get_or_build_workflow_engine(
                is_release,
                chat_vo,
                app_alias_id,
                workflow_dsl,
                workflow_dsl_update_time,
                span_context,
            )
            sparkflow_engine = copy.deepcopy(src_sparkflow_engine)

            # Initialize streaming processing components
            need_order_stream_result_q: asyncio.Queue[Any] = asyncio.Queue()
            structured_data: dict[Any, Any] = {}
            support_stream_node_id_queue: asyncio.Queue[Any] = asyncio.Queue()

            # Initialize model content output queues
            await _init_stream_q(
                sparkflow_engine.engine_ctx.msg_or_end_node_deps,
                sparkflow_engine.engine_ctx.variable_pool,
            )

            callbacks, consumer_tasks = await _init_callbacks_and_consumers(
                sparkflow_engine,
                response_queue,
                need_order_stream_result_q,
                support_stream_node_id_queue,
                structured_data,
                span_context,
                event_id,
                chat_vo.flow_id,
            )

            # Validate file inputs (if any)
            await _validate_file_inputs(workflow_dsl, chat_vo, span_context)

            # Get chat history records
            history = await _get_chat_history(sparkflow_engine, chat_vo, span_context)

            # Perform input content audit
            if app_audit_policy == AppAuditPolicy.AGENT_PLATFORM:
                await _perform_input_audit(chat_vo, span)

            # Execute workflow
            await callbacks.on_sparkflow_start()

            result = await sparkflow_engine.async_run(
                inputs=chat_vo.parameters,
                callback=callbacks,
                span=span_context,
                history=history,
                history_v2=chat_vo.history,
                event_log_trace=workflow_trace,
            )

            # Process results and upload trace information
            await _process_and_report_result(
                result, workflow_trace, span_context, consumer_tasks
            )

            await callbacks.on_sparkflow_end(message=result)
            m.in_success_count()
        except CustomException as err:
            llm_resp = LLMGenerate.workflow_end_error(
                sid=span.sid, code=err.code, message=err.message
            )
            await response_queue.put(llm_resp)
            span_context.record_exception(err)
            m.in_error_count(err.code, span=span_context)
            code = err.code
            error_message = err.message
        except Exception as err:
            llm_resp = LLMGenerate.workflow_end_error(
                sid=span.sid,
                code=CodeEnum.PROTOCOL_VALIDATION_ERROR.code,
                message=str(err),
            )
            await response_queue.put(llm_resp)
            span_context.record_exception(err)
            m.in_error_count(CodeEnum.OPEN_API_ERROR.code, span=span_context)
            code = CodeEnum.OPEN_API_ERROR.code
            error_message = CodeEnum.OPEN_API_ERROR.msg
        finally:
            kafka_report(
                span=span_context,
                workflow_log=workflow_trace,
                code=code,
                message=error_message,
            )
            # Ensure resource cleanup
            await _cleanup_resources(
                consumer_tasks if "consumer_tasks" in locals() else []
            )


async def _init_stream_q(
    msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo], variable_pool: VariablePool
) -> None:
    """
    Initialize streaming response queues for message and end nodes.

    This function sets up streaming queues for nodes that support streaming output,
    enabling real-time data flow between nodes.

    :param msg_or_end_node_deps: Dictionary mapping node IDs to their dependency
                                 information
    :param variable_pool: Variable pool containing streaming data structures
    :return: None
    """
    for msg_or_end_node_id, msg_or_end_dep_info in msg_or_end_node_deps.items():
        for data_source_node_id in msg_or_end_dep_info.data_dep:

            if data_source_node_id.split(":")[0] not in [
                NodeType.LLM.value,
                NodeType.AGENT.value,
                NodeType.KNOWLEDGE_PRO.value,
                NodeType.FLOW.value,
            ]:
                continue

            if msg_or_end_node_id not in variable_pool.stream_data:
                variable_pool.stream_data[msg_or_end_node_id] = {}

            if data_source_node_id not in variable_pool.stream_data[msg_or_end_node_id]:
                variable_pool.stream_data[msg_or_end_node_id][
                    data_source_node_id
                ] = asyncio.Queue()


def change_dsl_triplets(
    spark_dsl: dict, app_id: str, api_key: str, api_secret: str
) -> dict:
    """
    Replace model provider triplets (app_id, api_key, api_secret) in workflow DSL.

    This function replaces the model provider credentials in the workflow DSL
    for open API usage scenarios.

    :param spark_dsl: Original workflow DSL dictionary
    :param app_id: Application ID for model provider
    :param api_key: API key for model provider
    :param api_secret: API secret for model provider
    :return: Modified DSL dictionary with updated credentials
    """
    dsl = copy.deepcopy(spark_dsl)
    nodes = dsl["data"]["nodes"]
    for index, node in enumerate(nodes):
        node_id = node["id"]
        if node_id.split(":")[0] in [
            NodeType.LLM.value,
            NodeType.DECISION_MAKING.value,
            NodeType.PARAMETER_EXTRACTOR.value,
            NodeType.AGENT.value,
            NodeType.DATABASE.value,
        ]:
            model_source = (
                node["data"]
                .get("nodeParam", {})
                .get("source", ModelProviderEnum.XINGHUO.value)
            )
            if model_source != ModelProviderEnum.OPENAI.value:
                dsl["data"]["nodes"][index]["data"]["nodeParam"]["appId"] = app_id
                dsl["data"]["nodes"][index]["data"]["nodeParam"]["apiKey"] = api_key
                dsl["data"]["nodes"][index]["data"]["nodeParam"][
                    "apiSecret"
                ] = api_secret

    return dsl


async def _get_response(
    app_audit_policy: AppAuditPolicy,
    audit_strategy: Optional[AuditStrategy],
    response_queue: asyncio.Queue,
) -> LLMGenerate:
    """
    Get response data from appropriate queue based on audit policy and strategy.

    This function retrieves LLMGenerate objects from different queues depending
    on whether audit processing is enabled.

    :param app_audit_policy: Application audit policy configuration
    :param audit_strategy: Optional audit strategy for content moderation
    :param response_queue: Default response queue for non-audited responses
    :return: LLMGenerate object containing response data
    :raises Exception: When timeout occurs or audit processing fails
    """
    response: LLMGenerate
    if app_audit_policy == AppAuditPolicy.AGENT_PLATFORM and audit_strategy:
        frame_audit_result: FrameAuditResult = await asyncio.wait_for(
            audit_strategy.context.output_queue.get(), timeout=120
        )
        if frame_audit_result.error:
            raise frame_audit_result.error
        response = cast(LLMGenerate, frame_audit_result.source_frame)
    else:
        response = await asyncio.wait_for(response_queue.get(), timeout=120)
    return response


async def _get_resume_response(
    event: Event, audit_strategy: AuditStrategy | None
) -> LLMGenerate:
    response: LLMGenerate
    if audit_strategy:
        frame_audit_result: FrameAuditResult = await asyncio.wait_for(
            audit_strategy.context.output_queue.get(), timeout=100
        )
        if frame_audit_result.error:
            raise frame_audit_result.error

        response = cast(LLMGenerate, frame_audit_result.source_frame)
    else:
        res = await asyncio.wait_for(
            EventRegistry().fetch_resume_data(
                queue_name=event.get_workflow_q_name(), timeout=event.timeout
            ),
            event.timeout,
        )
        data = json.loads(res.get("message", "{}"))
        response = LLMGenerate.model_validate(data)
    return response


def _deal_streaming_step(
    response_frame: LLMGenerate, last_workflow_step: WorkflowStep
) -> None:
    """
    Process streaming response frame and update workflow step information.

    This function handles sequence numbering and progress tracking for
    streaming responses to ensure proper ordering and progress display.

    :param response_frame: Current response frame to process
    :param last_workflow_step: Previous workflow step for sequence tracking
    :return: None
    """
    last_workflow_step.seq += 1

    if not response_frame.workflow_step:
        return

    response_frame.workflow_step.seq = last_workflow_step.seq

    # Ensure progress does not regress
    response_frame.workflow_step.progress = round(
        response_frame.workflow_step.progress, 2
    )
    if response_frame.workflow_step.progress < last_workflow_step.progress:
        response_frame.workflow_step.progress = last_workflow_step.progress
    else:
        last_workflow_step.progress = response_frame.workflow_step.progress


def _filter_response_frame(
    response_frame: LLMGenerate,
    is_stream: bool,
    last_workflow_step: WorkflowStep,
    message_cache: list,
    reasoning_content_cache: list,
    is_release: bool,
) -> Tuple[Optional[LLMGenerate], bool]:
    """
    Filter or process a response frame based on node type, content, and streaming state.

    This function determines whether to keep a response frame based on various
    criteria including node type, content validity, and streaming configuration.

    :param response_frame: Current response frame to process
    :param is_stream: Whether this is a streaming response
    :param last_workflow_step: Previous workflow step for tracking frame index
                               and progress
    :param message_cache: Cached content for non-streaming mode
    :param reasoning_content_cache: Cached reasoning content for non-streaming mode
    :param is_release: Whether running in production release mode
    :return: Tuple of (filtered response frame or None, whether to keep the response
             frame)
    """

    if not is_release:
        return response_frame, True

    node_id = (
        response_frame.workflow_step.node.id
        if response_frame.workflow_step and response_frame.workflow_step.node
        else ""
    )
    node_type_prefix = node_id.split(":")[0]
    choice = response_frame.choices[0]
    delta = choice.delta

    # Only process specific node types (flow_obj or MESSAGE/END/QUESTION_ANSWER)
    valid_node_types = {
        NodeType.MESSAGE.value,
        NodeType.END.value,
        NodeType.QUESTION_ANSWER.value,
    }
    is_valid_node = node_id == "flow_obj" or node_type_prefix in valid_node_types
    if not is_valid_node:
        return None, False

    # Filter out frames with empty content unless it's a valid stop or interrupt
    is_content_empty = not delta.content and not delta.reasoning_content
    is_valid_stop = (
        node_id == "flow_obj" and choice.finish_reason == ChatStatus.FINISH_REASON.value
    )
    is_interrupted = choice.finish_reason == ChatStatus.INTERRUPT.value
    if is_content_empty and not (is_valid_stop or is_interrupted):
        return None, False

    # Handle streaming mode
    if is_stream:
        _deal_streaming_step(response_frame, last_workflow_step)
        # flow_obj node should not display content
        if node_id == "flow_obj":
            delta.content = ""

    # Handle non-streaming mode
    else:
        if node_type_prefix in {NodeType.MESSAGE.value, NodeType.END.value}:
            # Cache content, wait for flow_obj stop to return unified result
            message_cache.append(delta.content)
            reasoning_content_cache.append(delta.reasoning_content)
            return None, False

        elif (
            node_id == "flow_obj"
            and choice.finish_reason == ChatStatus.FINISH_REASON.value
        ):
            # Concatenate cached content
            delta.content = "".join(message_cache)
            delta.reasoning_content = "".join(reasoning_content_cache)

    # Remove unnecessary fields to reduce response data size
    if response_frame.workflow_step and response_frame.workflow_step.node:
        response_frame.workflow_step.node = None

    if not is_stream and response_frame.workflow_step:
        response_frame.workflow_step = None

    # Standardize index
    choice.index = 0

    return response_frame, True


async def _chat_response_stream(
    response_queue: Queue,
    flow_id: str,
    app_audit_policy: AppAuditPolicy,
    event_id: str,
    is_stream: bool,
    is_release: bool,
    span: Span,
) -> AsyncIterator[str]:
    """
    Process chat response streaming queue and generate streaming output.

    This function handles the streaming response processing, including audit
    integration, response filtering, and error handling.

    :param response_queue: Queue containing workflow execution responses
    :param flow_id: Workflow identifier for tracking
    :param app_audit_policy: Application audit policy for content moderation
    :param event_id: Unique event identifier for tracking
    :param is_stream: Whether to enable streaming mode
    :param is_release: Whether running in production release environment
    :param span: Distributed tracing span for monitoring
    :return: AsyncIterator yielding streaming response strings
    """

    message_cache: List[str] = []
    reasoning_content_cache: List[str] = []
    final_content = ""
    final_reasoning_content = ""
    last_workflow_step = WorkflowStep(seq=0, progress=0)

    with span.start(attributes={"flow_id": flow_id}) as span_context:

        # Initialize audit-related components
        audit_strategy, task = await _init_audit_policy(
            app_audit_policy, response_queue, span_context
        )

        response = None
        try:
            while True:
                response = await _get_response(
                    app_audit_policy, audit_strategy, response_queue
                )

                response, should_return = _filter_response_frame(
                    response_frame=response,
                    is_stream=is_stream,
                    last_workflow_step=last_workflow_step,
                    message_cache=message_cache,
                    reasoning_content_cache=reasoning_content_cache,
                    is_release=is_release,
                )

                if not should_return:
                    continue

                if not response:
                    raise CustomException(CodeEnum.OPEN_API_ERROR)

                # deal with event data
                if response.event_data:
                    yield await _del_response_resume_data(
                        app_audit_policy, response, is_stream, event_id
                    )
                    # forward queue messages
                    _ = asyncio.create_task(
                        _forward_queue_messages(
                            app_audit_policy,
                            audit_strategy,
                            response_queue,
                            event_id,
                            span_context,
                        )
                    )
                    return

                final_content += response.choices[0].delta.content
                final_reasoning_content += response.choices[0].delta.reasoning_content
                span_context.add_info_events(
                    {
                        "llm_resp": json.dumps(
                            response.model_dump(exclude_none=True),
                            ensure_ascii=False,
                        )
                    }
                )
                yield Streaming.generate_data(response.model_dump(exclude_none=True))

                if response.choices[0].finish_reason == ChatStatus.FINISH_REASON.value:
                    # Exit condition met
                    EventRegistry().on_finished(event_id=event_id)
                    return

        except asyncio.TimeoutError:
            llm_resp = LLMGenerate.workflow_end_open_error(
                code=CodeEnum.OPEN_API_STREAM_QUEUE_TIMEOUT_ERROR.code,
                message=CodeEnum.OPEN_API_STREAM_QUEUE_TIMEOUT_ERROR.msg,
                sid=span_context.sid,
            )
            span_context.add_info_events(
                {
                    "llm_resp": json.dumps(
                        llm_resp.model_dump(exclude_none=True), ensure_ascii=False
                    )
                }
            )
            yield Streaming.generate_data(llm_resp.model_dump(exclude_none=True))
            return
        except CustomException as e:
            llm_resp = LLMGenerate.workflow_end_open_error(
                code=e.code,
                message=e.message,
                sid=span_context.sid,
            )
            span_context.add_info_events(
                {
                    "llm_resp": json.dumps(
                        llm_resp.model_dump(exclude_none=True), ensure_ascii=False
                    )
                }
            )
            yield Streaming.generate_data(llm_resp.model_dump(exclude_none=True))
            return
        except Exception:
            llm_resp = LLMGenerate.workflow_end_open_error(
                code=CodeEnum.OPEN_API_ERROR.code,
                message=CodeEnum.OPEN_API_ERROR.msg,
                sid=span_context.sid,
            )
            span_context.add_info_events(
                {
                    "llm_resp": json.dumps(
                        llm_resp.model_dump(exclude_none=True), ensure_ascii=False
                    )
                }
            )
            yield Streaming.generate_data(llm_resp.model_dump(exclude_none=True))
            return
        finally:
            if task:
                await audit_service.audit_task_cancel(task)
            if response and (
                response.event_data
                or response.choices[0].finish_reason == ChatStatus.FINISH_REASON.value
            ):
                span.add_info_event(
                    f"Workflow output data processed through audit:\n"
                    f"final_content: {final_content}, \n"
                    f"final_reasoning_content: {final_reasoning_content}"
                )


async def _forward_queue_messages(
    app_audit_policy: AppAuditPolicy,
    audit_strategy: AuditStrategy | None,
    response_queue: asyncio.Queue,
    event_id: str,
    span: Span,
) -> None:
    """
    Forward queue messages to event registry.

    :param app_audit_policy: Application audit policy configuration
    :param audit_strategy: Audit strategy configuration
    :param response_queue: Response queue
    :param event_id: Event identifier
    :param span: Span
    """
    try:
        while True:
            response = await _get_response(
                app_audit_policy, audit_strategy, response_queue
            )
            event = EventRegistry().get_event(event_id=event_id)
            data = json.dumps(response.dict(), ensure_ascii=False)
            await EventRegistry().write_resume_data(
                queue_name=event.get_workflow_q_name(),
                data=data,
                expire_time=event.timeout,
            )
            if response.choices[0].finish_reason == ChatStatus.FINISH_REASON.value:
                return
    except Exception as e:
        span.record_exception(e)
        raise e


async def _del_response_resume_data(
    app_audit_policy: AppAuditPolicy,
    response: LLMGenerate,
    is_stream: bool,
    event_id: str,
) -> str:
    """
    Handle response resume data delivery for interrupted workflows.

    This function processes resume data for interrupted workflows and handles
    audit policy restrictions for question-answer nodes.

    :param app_audit_policy: Application audit policy configuration
    :param response: LLMGenerate response object
    :param is_stream: Whether streaming mode is enabled
    :param event_id: Unique event identifier
    :return: Streaming data string
    :raises CustomException: When audit policy doesn't support QA nodes
    """
    # Question-answer nodes currently don't support audit
    if app_audit_policy == AppAuditPolicy.AGENT_PLATFORM:
        raise CustomException(CodeEnum.AUDIT_QA_ERROR)
    EventRegistry().on_interrupt(event_id=event_id)
    return Streaming.generate_data(response.model_dump(exclude_none=True))


async def _init_audit_policy(
    app_audit_policy: AppAuditPolicy, response_queue: asyncio.Queue, span: Span
) -> Tuple[Optional[AuditStrategy], Optional[asyncio.Task]]:
    """
    Initialize audit policy and strategy for content moderation.

    This function sets up the audit strategy and starts the audit task
    if the application audit policy requires content moderation.

    :param app_audit_policy: Application audit policy configuration
    :param response_queue: Response queue for audit processing
    :param span: Distributed tracing span for monitoring
    :return: Tuple of (audit strategy, audit task) or (None, None) if not needed
    """
    if app_audit_policy == AppAuditPolicy.AGENT_PLATFORM:
        audit_strategy = TextAuditStrategy(
            chat_sid=span.sid,
            audit_apis=[IFlyAuditAPI()],
            chat_app_id=span.app_id,
            uid=span.uid,
        )
        task = asyncio.create_task(
            audit_service.response_audit(response_queue, audit_strategy, span)
        )
        return audit_strategy, task
    return None, None


async def chat_resume_response_stream(
    span: Span, event_id: str, audit_policy: int, is_release: bool
) -> AsyncGenerator[str, None]:
    """
    Resume chat response streaming for interrupted workflows.

    This function handles resuming streaming responses for workflows that were
    previously interrupted, allowing users to continue from where they left off.

    :param span: Distributed tracing span for monitoring
    :param event_id: Unique event identifier for the interrupted workflow
    :param audit_policy: Audit policy configuration
    :param is_release: Whether running in production release environment
    :return: AsyncGenerator yielding streaming response strings
    """
    event = EventRegistry().get_event(event_id=event_id)

    message_cache: List[str] = []
    reasoning_content_cache: List[str] = []
    is_stream = event.is_stream

    final_content = ""
    final_reasoning_content = ""
    last_workflow_step = WorkflowStep(seq=0, progress=0)

    while True:
        try:
            # Question-answer supports audit
            if audit_policy == AppAuditPolicy.AGENT_PLATFORM.value and event_id:
                raise CustomException(CodeEnum.AUDIT_QA_ERROR)

            src_response: LLMGenerate = await _get_resume_response(event, None)

            span.add_info_events(
                {
                    "response": json.dumps(
                        src_response.model_dump(exclude_none=True), ensure_ascii=False
                    )
                }
            )

            response, should_return = _filter_response_frame(
                response_frame=src_response,
                is_stream=is_stream,
                last_workflow_step=last_workflow_step,
                message_cache=message_cache,
                reasoning_content_cache=reasoning_content_cache,
                is_release=is_release,
            )
            if not should_return:
                continue

            if not response:
                raise CustomException(CodeEnum.OPEN_API_ERROR)

            if response and response.event_data:
                EventRegistry().on_interrupt(event_id=event_id)
                response.id = span.sid
                yield Streaming.generate_data(response.model_dump(exclude_none=True))
                return

            final_content += response.choices[0].delta.content
            final_reasoning_content += response.choices[0].delta.reasoning_content

            span.add_info_events(
                {
                    "llm_resp": json.dumps(
                        response.model_dump(exclude_none=True), ensure_ascii=False
                    )
                }
            )
            response.id = span.sid
            yield Streaming.generate_data(response.model_dump(exclude_none=True))

            if response.choices[0].finish_reason == ChatStatus.FINISH_REASON.value:
                span.add_info_event(
                    f"Workflow output data processed through audit:\n"
                    f"final_content: {final_content}, \n"
                    f"final_reasoning_content: {final_reasoning_content}"
                )
                # Exit condition met
                EventRegistry().on_finished(event_id=event_id)
                return

        except (Exception, asyncio.TimeoutError, CustomException) as e:
            code = CodeEnum.OPEN_API_STREAM_QUEUE_TIMEOUT_ERROR.code
            message = CodeEnum.OPEN_API_STREAM_QUEUE_TIMEOUT_ERROR.msg

            if isinstance(e, CustomException):
                code = e.code
                message = e.message
            llm_resp = LLMGenerate.workflow_end_error(
                code=code,
                message=message,
                sid=span.sid,
            )
            span.add_info_events(
                {
                    "llm_resp": json.dumps(
                        llm_resp.model_dump(exclude_none=True), ensure_ascii=False
                    )
                }
            )
            EventRegistry().on_finished(event_id=event_id)
            llm_resp.id = span.sid
            yield Streaming.generate_data(llm_resp.model_dump(exclude_none=True))
            return
