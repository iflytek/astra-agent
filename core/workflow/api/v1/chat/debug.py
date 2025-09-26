import json
from typing import Annotated, Optional, Union

from fastapi import APIRouter, Header
from starlette.responses import JSONResponse, StreamingResponse

from workflow.cache.event_registry import Event, EventRegistry
from workflow.consts.app_audit import AppAuditPolicy
from workflow.consts.engine.chat_status import ChatStatus
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

router = APIRouter(tags=["SSE_DEBUG_CHAT"])


@router.post("/debug/chat/completions", response_model=None)
async def chat_debug(
    x_consumer_username: Annotated[str, Header()],
    chat_vo: ChatVo,
) -> Union[StreamingResponse, JSONResponse]:
    """
    Handle debug chat completions
    :param x_consumer_username: Consumer username from header
    :param chat_vo: Chat request data
    :return: Streaming or JSON response
    """
    app_id = x_consumer_username
    m = Meter(app_id)

    span = Span(app_id=app_id, uid=chat_vo.uid, chat_id=chat_vo.chat_id)
    with span.start(
        attributes={"flow_id": chat_vo.flow_id},
    ) as span_context:
        m.set_label("flow_id", chat_vo.flow_id)
        try:
            session = next(get_session())

            if chat_vo.version:
                db_flow = flow_service.get_latest_published_flow_by(
                    chat_vo.flow_id, app_id, session, span_context, chat_vo.version
                )
            else:
                db_flow = flow_service.get(chat_vo.flow_id, session, span)

            app_info = app_service.get_info(app_id, session, span)

            event = Event(
                flow_id=chat_vo.flow_id,
                app_id=app_id,
                event_id=str(get_id()),
                uid=chat_vo.uid,
                chat_id=chat_vo.chat_id,
            )
            EventRegistry().init_event(event)
            app_audit_policy = (
                AppAuditPolicy.DEFAULT
                if app_info.audit_policy == AppAuditPolicy.DEFAULT.value
                else AppAuditPolicy.AGENT_PLATFORM
            )
            return await Streaming.send(
                await chat_service.event_stream(
                    app_id,
                    event.event_id,
                    db_flow.data,
                    db_flow.update_at,
                    chat_vo,
                    False,
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


@router.post("/debug/resume", response_model=None)
async def resume_debug(request: ResumeVo) -> Union[StreamingResponse, JSONResponse]:
    """
    Resume debug chat event
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
                    is_release=False,
                ),
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
