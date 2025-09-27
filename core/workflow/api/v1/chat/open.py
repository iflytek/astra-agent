"""
Open API chat endpoints for workflow system.

This module provides open API endpoints for chat completions and resume functionality,
including platform-specific publishing validation and audit policies.
"""

import json
import os
from typing import Annotated, Optional, Union

from fastapi import APIRouter, Depends, Header

from workflow.consts.engine.chat_status import ChatStatus
from workflow.consts.runtime_env import RuntimeEnv

try:
    from sqlmodel import Session  # type: ignore[import]
except ImportError:
    from sqlalchemy.orm import Session  # type: ignore[assignment]

from starlette.responses import JSONResponse, StreamingResponse

from workflow.cache.event_registry import Event, EventRegistry
from workflow.consts.app_audit import AppAuditPolicy
from workflow.consts.tenant_publish_matrix import Platform, TenantPublishMatrix
from workflow.domain.entities.chat import ChatVo, ResumeVo
from workflow.domain.entities.response import Streaming
from workflow.engine.callbacks.openai_types_sse import LLMGenerate
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.middleware.getters import get_session
from workflow.extensions.otlp.metric.meter import Meter
from workflow.extensions.otlp.trace.span import Span
from workflow.service import app_service, audit_service, chat_service, flow_service
from workflow.utils.snowfake import get_id

router = APIRouter(tags=["SSE_OPENAPI"])


@router.post("/chat/completions", response_model=None)
async def chat_open(
    x_consumer_username: Annotated[str, Header()],
    chat_vo: ChatVo,
    db_session: Session = Depends(get_session),
) -> Union[StreamingResponse, JSONResponse]:
    """
    Handle chat completions for open API
    :param x_consumer_username: Consumer username from header
    :param chat_vo: Chat request data
    :param db_session: Database session dependency
    :return: Streaming or JSON response
    """
    m = Meter()
    app_id = x_consumer_username

    span = Span(app_id=app_id, uid=chat_vo.uid, chat_id=chat_vo.chat_id)
    with span.start(
        attributes={"flow_id": chat_vo.flow_id},
    ) as span_context:
        try:
            db_flow = flow_service.get_latest_published_flow_by(
                chat_vo.flow_id, app_id, db_session, span_context, chat_vo.version
            )
            spark_dsl = db_flow.release_data
            app_info = app_service.get_info(app_id, db_session, span)

            app_audit_policy = (
                AppAuditPolicy.DEFAULT
                if not app_info.audit_policy
                or app_info.audit_policy == AppAuditPolicy.DEFAULT.value
                else AppAuditPolicy.AGENT_PLATFORM
            )

            # Validate flow platform publishing permissions
            if (db_flow.release_status == 0) or (
                (
                    db_flow.release_status
                    & TenantPublishMatrix(Platform.XINGCHEN).get_take_off
                )
                and (
                    db_flow.release_status
                    & TenantPublishMatrix(Platform.KAI_FANG).get_take_off
                )
                and (
                    db_flow.release_status
                    & TenantPublishMatrix(Platform.AI_UI).get_take_off
                )
            ):
                return await Streaming.send_error(
                    LLMGenerate.workflow_end_error(
                        span_context.sid,
                        CodeEnum.FLOW_NOT_PUBLISH_ERROR.code,
                        CodeEnum.FLOW_NOT_PUBLISH_ERROR.msg,
                    ).dict(),
                    JSONResponse,
                )

            if not os.getenv("RUNTIME_ENV", RuntimeEnv.Local.value) in [
                RuntimeEnv.Dev.value,
                RuntimeEnv.Test.value,
            ]:
                # Replace app_id, api_key, api_secret in protocol
                db_flow.release_data = chat_service.change_dsl_triplets(
                    spark_dsl,
                    app_id=app_id,
                    api_key=app_info.api_key,
                    api_secret=app_info.api_secret,
                )

            event = Event(
                flow_id=chat_vo.flow_id,
                app_id=app_id,
                event_id=str(get_id()),
                uid=chat_vo.uid,
                chat_id=chat_vo.chat_id,
                is_stream=chat_vo.stream,
            )
            EventRegistry().init_event(event)

            return await Streaming.send(
                await chat_service.event_stream(
                    app_id,
                    event.event_id,
                    db_flow.release_data,
                    db_flow.update_at,
                    chat_vo,
                    True,
                    app_audit_policy,
                    span_context,
                ),
                StreamingResponse if chat_vo.stream else JSONResponse,
            )
        except CustomException as err:
            m.in_error_count(err.code)
            span_context.record_exception(err)
            return await Streaming.send_error(
                LLMGenerate.workflow_end_error(
                    span_context.sid, err.code, err.message
                ).dict(),
                JSONResponse,
            )

        except Exception as err:
            m.in_error_count(CodeEnum.OPEN_API_ERROR.code)
            span_context.record_exception(err)
            return await Streaming.send_error(
                LLMGenerate.workflow_end_error(
                    span_context.sid,
                    CodeEnum.OPEN_API_ERROR.code,
                    CodeEnum.OPEN_API_ERROR.msg,
                ).dict(),
                JSONResponse,
            )


@router.post("/resume", response_model=None)
async def resume_open(request: ResumeVo) -> Union[StreamingResponse, JSONResponse]:
    """
    Resume an interrupted chat event
    :param request: Resume request data
    :return: Streaming or JSON response
    """
    event_id = request.event_id
    event_type = request.event_type
    content = request.content
    span = Span(app_id="", uid="", chat_id="")
    m = Meter()

    with span.start(
        attributes={"event_id": event_id},
    ) as span_context:

        try:
            event: Optional[Event] = EventRegistry().get_event(event_id=event_id)
            if event is None:
                raise CustomException(
                    CodeEnum.EVENT_REGISTRY_NOT_FOUND_ERROR,
                    "Event not found",
                )

            m.set_label("flow_id", event.flow_id)
            m.set_label("app_id", event.app_id)

            span.app_id = event.app_id
            span.uid = event.uid
            span.chat_id = event.chat_id

            span_context.add_info_events(
                {"resume_event": json.dumps(event.dict(), ensure_ascii=False)}
            )

            if not event.status == ChatStatus.INTERRUPT.value:
                raise CustomException(
                    CodeEnum.EVENT_REGISTRY_NOT_FOUND_ERROR,
                    "Current event is not paused",
                )

            # Input audit
            session = next(get_session())
            app_info = app_service.get_info(event.app_id, session, span)
            if app_info.audit_policy == AppAuditPolicy.AGENT_PLATFORM.value:
                await audit_service.input_audit(content, span)

            await EventRegistry().write_resume_data(
                queue_name=event.get_node_q_name(),
                data=json.dumps(
                    {"event_type": event_type, "content": content}, ensure_ascii=False
                ),
                expire_time=event.timeout,
            )

            return await Streaming.send(
                chat_service.chat_resume_response_stream(
                    span=span_context,
                    event_id=event_id,
                    audit_policy=app_info.audit_policy,
                    is_release=True,
                )
            )

        except CustomException as err:
            span_context.record_exception(err)
            m.in_error_count(err.code, span=span_context)
            return await Streaming.send_error(
                LLMGenerate.workflow_end_error(
                    sid=span.sid, code=err.code, message=err.message
                ).dict(),
                JSONResponse,
            )
        except Exception as e:
            span_context.record_exception(e)
            m.in_error_count(CodeEnum.OPEN_API_ERROR.code, span=span_context)
            return await Streaming.send_error(
                LLMGenerate.workflow_end_error(
                    sid=span.sid,
                    code=CodeEnum.EVENT_REGISTRY_NOT_FOUND_ERROR.code,
                    message=CodeEnum.EVENT_REGISTRY_NOT_FOUND_ERROR.msg,
                ).dict(),
                JSONResponse,
            )
