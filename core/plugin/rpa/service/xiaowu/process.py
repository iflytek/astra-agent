"""Task processing module containing task creation and monitoring logic."""

import asyncio
import os
import time
from typing import AsyncGenerator, Optional, Tuple, cast

import httpx
from common.otlp.log_trace.node_trace_log import NodeTraceLog, Status
from common.otlp.metrics.meter import Meter
from common.otlp.trace.span import Span
from common.service import get_kafka_producer_service
from common.service.kafka.kafka_service import KafkaProducerService
from fastapi import HTTPException
from loguru import logger
from plugin.rpa.api.schemas.execution_schema import RPAExecutionResponse
from plugin.rpa.consts import const
from plugin.rpa.errors.error_code import ErrorCode
from plugin.rpa.exceptions.config_exceptions import InvalidConfigException
from plugin.rpa.infra.xiaowu.tasks import create_task, query_task_status


async def task_monitoring(
    sid: Optional[str],
    access_token: str,
    project_id: str,
    exec_position: Optional[str],
    params: Optional[dict],
) -> AsyncGenerator[str, None]:
    """
    Monitor task status.
    - Send "ping" every ping_interval seconds.
    - Query task status every task_query_interval seconds.
    - Return task result when task is completed.
    - Return "timeout" if timeout (timeout_sec seconds) is reached.
    :param task_query_interval: Task query interval (seconds), default 10 seconds.
    """
    logger.debug(
        f"Starting task monitoring for project_id: {project_id}, "
        f"exec_position: {exec_position}, params: {params}"
    )
    req = (
        f"sid:{sid}, access_token:{access_token}, project_id:{project_id}"
        f"exec_position:{exec_position}, params:{params}"
    )
    span, node_trace = setup_span_and_trace(req=req, sid=sid)
    if not sid:
        sid = span.sid

    with span.start(func_name="task_monitoring") as span_context:
        node_trace.sid = span_context.sid
        node_trace.chat_id = span_context.sid
        meter = setup_logging_and_metrics(
            span_context=span_context, req=req, product_id=project_id
        )

        task_id = None
        try:
            task_id = await create_task(
                access_token=access_token,
                project_id=project_id,
                exec_position=exec_position,
                params=params,
            )  # Create task
        except (
            InvalidConfigException,
            HTTPException,
            httpx.HTTPStatusError,
            httpx.RequestError,
            AssertionError,
            KeyError,
            AttributeError,
        ) as e:
            logger.error(f"error: {e}")
            code = ErrorCode.CREATE_TASK_ERROR.code
            msg = f"{ErrorCode.CREATE_TASK_ERROR.message}, detail: {e}"
            error = RPAExecutionResponse(code=code, message=msg, sid=sid)
            yield error.model_dump_json()
            otlp_handle(
                meter=meter,
                node_trace=node_trace,
                code=ErrorCode.CREATE_TASK_ERROR.code,
                message=msg,
            )
            return

        start_time = time.time()
        while (time.time() - start_time) < int(
            os.getenv(const.XIAOWU_RPA_TIMEOUT_KEY, "300")
        ):
            span_context.add_info_events(attributes={"query sleep": str(time.time())})
            await asyncio.sleep(
                int(os.getenv(const.XIAOWU_RPA_TASK_QUERY_INTERVAL_KEY, "10"))
            )
            span_context.add_info_events(attributes={"query start": str(time.time())})
            result = await query_task_status(access_token, task_id)  # Query task
            span_context.add_info_events(attributes={"query finish": str(result)})
            if result:
                code, msg, data = result
                if code == ErrorCode.SUCCESS.code:
                    success = RPAExecutionResponse(
                        code=code, message=msg, data=data, sid=sid
                    )
                    yield success.model_dump_json()
                    otlp_handle(
                        meter=meter,
                        node_trace=node_trace,
                        code=ErrorCode.SUCCESS.code,
                        message=ErrorCode.SUCCESS.message,
                    )
                elif code == ErrorCode.QUERY_TASK_ERROR.code:
                    error = RPAExecutionResponse(code=code, message=msg, sid=sid)
                    yield error.model_dump_json()
                    otlp_handle(
                        meter=meter, node_trace=node_trace, code=code, message=msg
                    )
                return

        timeout = RPAExecutionResponse(
            code=ErrorCode.TIMEOUT_ERROR.code,
            message=ErrorCode.TIMEOUT_ERROR.message,
            sid=sid,
        )
        yield timeout.model_dump_json()
        otlp_handle(
            meter=meter,
            node_trace=node_trace,
            code=ErrorCode.TIMEOUT_ERROR.code,
            message=ErrorCode.TIMEOUT_ERROR.message,
        )


def setup_span_and_trace(req: str, sid: Optional[str]) -> Tuple[Span, NodeTraceLog]:
    """Setup span and node trace for the request."""
    span = Span()
    if sid:
        span.sid = sid

    node_trace = NodeTraceLog(
        service_id="",
        sid=sid or span.sid,
        app_id="defappid",
        uid="",
        chat_id=sid or "",
        sub="rpa-server",
        caller="",
        log_caller="",
        question=req,
    )
    return span, node_trace


def setup_logging_and_metrics(span_context: Span, req: str, product_id: str) -> Meter:
    """Setup logging and metrics for the request."""
    logger.info({"exec api, rap router usr_input": req})
    span_context.add_info_events({"usr_input": req})
    span_context.set_attributes(attributes={"tool_id": product_id})
    return Meter(app_id=span_context.app_id, func="task_monitoring")


def otlp_handle(
    meter: Meter, node_trace: NodeTraceLog, code: int, message: str
) -> None:
    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "false":
        return

    if code != 0:
        meter.in_error_count(code)
    else:
        meter.in_success_count()

    node_trace.answer = message
    node_trace.status = Status(
        code=code,
        message=message,
    )

    kafka_service = cast(KafkaProducerService, get_kafka_producer_service())
    node_trace.start_time = int(round(time.time() * 1000))
    kafka_service.send(
        topic=os.getenv(const.KAFKA_TOPIC_KEY, ""), value=node_trace.to_json()
    )
