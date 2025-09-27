import asyncio
import json
import logging
import uuid
from asyncio import Task
from typing import Any, Awaitable, Callable

from workflow.cache import event_registry
from workflow.cache.event_registry import Event, EventRegistry
from workflow.consts.engine.chat_status import ChatStatus
from workflow.engine.callbacks.openai_types_sse import LLMGenerate, WorkflowStep
from workflow.exception.e import CustomException, CustomExceptionCM
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.audit_system.audit_api.base import ContextList, Stage
from workflow.infra.audit_system.audit_api.iflytek.ifly_audit_api import IFlyAuditAPI
from workflow.infra.audit_system.base import (
    FrameAuditResult,
    InputFrameAudit,
    OutputFrameAudit,
)
from workflow.infra.audit_system.enums import Status
from workflow.infra.audit_system.orchestrator import AuditOrchestrator
from workflow.infra.audit_system.strategy.base_strategy import AuditStrategy
from workflow.infra.audit_system.strategy.text_strategy import TextAuditStrategy


def parse_frame_audit(response: LLMGenerate) -> OutputFrameAudit:
    """
    Convert LLMGenerate response to OutputFrameAudit object for audit processing.

    :param response: LLMGenerate object containing the response data
    :return: OutputFrameAudit object ready for audit processing
    """
    # Determine if empty frame needs audit
    none_need_audit = False

    # Extract content from either regular content or reasoning content
    content = (
        response.choices[0].delta.content
        if response.choices[0].delta.content
        else response.choices[0].delta.reasoning_content
    )

    # Check if this is an empty end frame that needs audit
    if content == "":
        # Determine if this is an empty end frame of a node, which requires audit
        if (
            response.workflow_step
            and response.workflow_step.node
            and response.workflow_step.node.finish_reason
            == ChatStatus.FINISH_REASON.value
        ):
            none_need_audit = True
    return OutputFrameAudit(
        content=content,
        status=(
            Status.NONE
            if response.choices[0].finish_reason != Status.STOP.value
            else Status.STOP
        ),
        frame_id=f"{str(uuid.uuid4())}->"
        f"{response.workflow_step.seq if response.workflow_step else 0}",
        stage=(
            Stage.REASONING
            if response.choices[0].delta.reasoning_content
            else Stage.ANSWER
        ),
        source_frame=response,
        none_need_audit=none_need_audit,
    )


async def response_audit(
    response_queue: asyncio.Queue, audit_strategy: AuditStrategy, span: Span
) -> None:
    """
    Process LLMGenerate objects from response queue and submit them for audit.

    :param response_queue: Queue containing LLMGenerate objects to be audited
    :param audit_strategy: Audit strategy to use for processing
    :param span: Tracing span for logging and monitoring
    :return: None
    """
    return await _common_response_audit(
        fetch_fn=lambda: response_queue.get(),
        audit_strategy=audit_strategy,
        span=span,
        initial_index=0,
    )


async def response_resume_audit(
    event: Event, audit_strategy: AuditStrategy, span: Span
) -> None:
    """
    Process LLMGenerate objects from interrupted response queue and submit them for audit.

    :param event: Event object containing workflow queue information
    :param audit_strategy: Audit strategy to use for processing
    :param span: Tracing span for logging and monitoring
    :return: None
    """

    async def fetch_resume() -> LLMGenerate:
        # Fetch resume data from event registry
        res = await EventRegistry().fetch_resume_data(
            queue_name=event.get_workflow_q_name(), timeout=event.timeout
        )
        # Parse JSON data and validate as LLMGenerate object
        data = json.loads(res.get("message", ""))
        return LLMGenerate.model_validate(data)

    return await _common_response_audit(
        fetch_fn=fetch_resume, audit_strategy=audit_strategy, span=span, initial_index=1
    )


async def _common_response_audit(
    fetch_fn: Callable[[], Awaitable[LLMGenerate]],
    audit_strategy: AuditStrategy,
    span: Span,
    initial_index: int = 0,
) -> None:
    """
    Common handler for processing LLMGenerate objects from response queue and submitting for audit.

    :param fetch_fn: Function to fetch LLMGenerate objects from the queue
    :param audit_strategy: Audit strategy to use for processing
    :param span: Tracing span for logging and monitoring
    :param initial_index: Starting index for frame numbering
    :return: None
    """
    temp_frame_index = initial_index
    final_content = ""
    final_reasoning_content = ""

    while True:
        try:
            # Allow main coroutine to exit quickly when needed
            await asyncio.sleep(0)
            response: LLMGenerate = await asyncio.wait_for(fetch_fn(), timeout=100)

            # Ensure workflow step is properly initialized
            response.workflow_step = (
                response.workflow_step if response.workflow_step else WorkflowStep()
            )
            response.workflow_step.seq = temp_frame_index

            # Check for error responses
            if response.code != 0:
                raise CustomExceptionCM(response.code, response.message)

            # Accumulate content for final logging
            final_content += response.choices[0].delta.content
            final_reasoning_content += response.choices[0].delta.reasoning_content

            # Handle question-answer node output content separately
            if response.event_data:
                event_id = response.event_data.event_id
                if event_id not in event_registry.EVENT_AUDIT_QUESTION_NO_IDX:
                    event_registry.EVENT_AUDIT_QUESTION_NO_IDX[event_id] = 0
                chat_sid = (
                    f"{span.sid}-{event_registry.EVENT_AUDIT_QUESTION_NO_IDX[event_id]}"
                )
                await output_audit(
                    content=json.dumps(response.event_data.value, ensure_ascii=False),
                    span=span,
                    source_frame=response.event_data.value,
                    chat_sid=chat_sid,
                )
                event_registry.EVENT_AUDIT_QUESTION_NO_IDX[event_id] += 1

            # Process frame for audit
            temp_frame_index += 1
            audit_orchestrator = AuditOrchestrator(audit_strategy)
            rr = parse_frame_audit(response)
            await audit_orchestrator.process_output(rr, span)

            # Check if this is the final response
            if response and (
                response.event_data
                or response.choices[0].finish_reason == Status.STOP.value
            ):
                span.add_info_event(
                    f"Workflow original output data result:\n"
                    f"final_content: {final_content}, \n"
                    f"final_reasoning_content: {final_reasoning_content}"
                )
                return

            # Allow main coroutine to exit quickly when needed
            await asyncio.sleep(0)
        except asyncio.TimeoutError as e:
            # Handle timeout errors by creating audit result with error
            ce = CustomException(
                CodeEnum.OPEN_API_STREAM_QUEUE_TIMEOUT_ERROR, cause_error=e
            )
            await audit_strategy.context.output_queue.put(
                FrameAuditResult(content="", status=Status.STOP, error=ce)
            )
        except Exception as e:
            # Handle other exceptions by wrapping in CustomException if needed
            if not isinstance(e, CustomException):
                e = CustomException(CodeEnum.AUDIT_ERROR, cause_error=e)
            await audit_strategy.context.output_queue.put(
                FrameAuditResult(content="", status=Status.STOP, error=e)
            )


async def node_debug_input_audit(sparkflow_dsl: dict, span: Span) -> None:
    """
    Submit node debug input for audit processing.

    :param sparkflow_dsl: DSL configuration containing node input data
    :param span: Tracing span for logging and monitoring
    :return: None
    """
    # Extract input content from DSL configuration
    content = {}

    for node in sparkflow_dsl.get("data", {}).get("nodes", [{}]):
        for input in node.get("data", {}).get("inputs", [{}]):
            name = input.get("name", "")
            value = input.get("schema", {}).get("value", {}).get("content", "")
            content[name] = value

    # Submit input for audit
    await input_audit(splice_input_content(content), span=span)


async def input_audit(
    content: str, span: Span, context_list: list[ContextList] = []
) -> None:
    """
    Submit input content for audit processing.

    :param content: Input content string to be audited
    :param span: Tracing span for logging and monitoring
    :param context_list: Optional list of context information for audit
    :return: None
    """

    # Create audit strategy with iFlytek audit API
    audit_strategy = TextAuditStrategy(
        chat_sid=span.sid,
        audit_apis=[IFlyAuditAPI()],
        chat_app_id=span.app_id,
        uid=span.uid,
    )

    # Process input through audit orchestrator
    audit_orchestrator = AuditOrchestrator(audit_strategy)
    await audit_orchestrator.process_input(
        InputFrameAudit(content=content, context_list=context_list), span
    )


def splice_input_content(content: dict) -> str:
    """
    Concatenate input content according to audit requirements.

    :param content: Dictionary containing input parameters and their values
    :return: Formatted string ready for audit processing
    """
    # Format each parameter as audit-ready string
    need_contents = []
    for key, value in content.items():
        need_contents.append(f'Parameter "{key}" input is "{value}"')
    return ",".join(need_contents) + "."


async def output_audit(
    content: str, span: Span, source_frame: Any = None, chat_sid: str = ""
) -> None:
    """
    Submit output content for audit processing.

    :param content: Output content string to be audited
    :param span: Tracing span for logging and monitoring
    :param source_frame: Optional source frame data for audit context
    :param chat_sid: Chat session ID, optional. If not provided, uses span.sid
    :return: None
    """
    # Use provided chat_sid or fall back to span.sid
    chat_sid = chat_sid if chat_sid else span.sid

    # Create audit strategy with iFlytek audit API
    audit_strategy = TextAuditStrategy(
        chat_sid=chat_sid,
        audit_apis=[IFlyAuditAPI()],
        chat_app_id=span.app_id,
        uid=span.uid,
    )
    audit_orchestrator = AuditOrchestrator(audit_strategy)

    with span.start() as context_span:
        # Split content into chunks of 1500 characters as required by audit system
        length = 1500
        sentences = [content[i : i + length] for i in range(0, len(content), length)]
        if not sentences:
            return

        # Process each chunk separately
        for i, sentence in enumerate(sentences):
            await audit_orchestrator.process_output(
                OutputFrameAudit(
                    frame_id=str(uuid.uuid4()),
                    content=sentence,
                    stage=Stage.ANSWER,
                    source_frame=source_frame,
                    status=Status.STOP if i == len(sentences) - 1 else Status.NONE,
                    not_need_submit=True,
                ),
                context_span,
            )
            # Check for audit errors and raise if found
            if audit_strategy.context.error:
                raise audit_strategy.context.error


async def audit_task_cancel(task: Task) -> None:
    """
    Cancel an audit task gracefully with timeout handling.

    :param task: The asyncio task to cancel
    :return: None
    """
    if task:
        task.cancel()  # Signal task to exit
        try:
            await asyncio.wait_for(task, timeout=1.0)  # Wait up to 1 second
        except asyncio.CancelledError:
            logging.error("Task was cancelled")
        except asyncio.TimeoutError:
            logging.error("Task didn't exit in time")
        except Exception as e:
            logging.error(f"Error during task cancellation, err: {str(e)}")
