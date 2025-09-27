"""
Flow layout and protocol management API endpoints.

This module provides API endpoints for managing workflow flows including
creation, updates, deletion, building, and comparison functionality.
"""

import json
from typing import Annotated, AsyncGenerator, cast

from fastapi import APIRouter, Depends, Header, status
from sqlalchemy import ColumnElement, and_

try:
    from sqlmodel import Session  # type: ignore[import]
except ImportError:
    from sqlalchemy.orm import Session  # type: ignore[assignment]

from starlette.responses import JSONResponse, StreamingResponse
from workflow.cache.flow import del_flow_by_id
from workflow.consts.comparisons import Tag
from workflow.consts.flow import FlowStatus
from workflow.domain.entities.compare_flow import DeleteComparisonVo, SaveComparisonVo
from workflow.domain.entities.flow import FlowRead, FlowUpdate
from workflow.domain.entities.response import (
    Streaming,
    response_error,
    response_success,
)
from workflow.domain.models.flow import Flow
from workflow.engine.dsl_engine import WorkflowEngineFactory
from workflow.engine.entities.workflow_dsl import WorkflowDSL
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.middleware.cache.base import BaseCacheService
from workflow.extensions.middleware.getters import get_cache_service, get_session
from workflow.extensions.otlp.metric.meter import Meter
from workflow.extensions.otlp.trace.span import Span
from workflow.service import app_service, flow_service, license_service

router = APIRouter(tags=["Flows"])


@router.post("/protocol/add", status_code=status.HTTP_200_OK)
def add(
    flow: Flow,
    session: Session = Depends(get_session),
) -> JSONResponse:
    """
    Add a new protocol flow
    :param flow: Flow data to be added
    :param session: Database session dependency
    :return: Response with flow_id
    """
    span = Span(app_id=flow.app_id or "")
    m = Meter(app_id=flow.app_id or "")
    with span.start(
        attributes={"flow_id": flow.id},
    ) as current_span:
        try:
            current_span.add_info_event(f"add flow vo: {flow.json()}")
            if flow.status not in [FlowStatus.DRAFT.value, FlowStatus.PUBLISHED.value]:
                raise CustomException(
                    err_code=CodeEnum.PROTOCOL_CREATE_ERROR,
                    err_msg=f"status value can only be 0 or 1, current value is {flow.status}",
                )

            app_info = app_service.get_info(flow.app_id, session, current_span)
            db_flow = flow_service.save(flow, app_info, session, current_span)

            if flow.data:
                try:
                    current_span.add_info_event("Protocol validation start")
                    sparkflow_protocol = flow.data
                    if isinstance(sparkflow_protocol, str):
                        sparkflow_protocol = json.loads(sparkflow_protocol)
                    WorkflowEngineFactory.create_engine(
                        WorkflowDSL.parse_obj(flow.data.get("data")), current_span
                    )
                    current_span.add_info_event("Protocol validation end")
                except CustomException as err:
                    current_span.record_exception(err)
                    raise err
            m.in_success_count()
            return response_success(
                {"flow_id": str(db_flow.id)},
                span.sid,
            )
        except CustomException as err:
            current_span.record_exception(err)
            m.in_error_count(err.code, span=current_span)
            return response_error(err.code, err.message, span.sid)
        except Exception as e:
            current_span.record_exception(e)
            m.in_error_count(CodeEnum.PROTOCOL_CREATE_ERROR.code, span=current_span)
            return response_error(
                CodeEnum.PROTOCOL_CREATE_ERROR.code,
                CodeEnum.PROTOCOL_CREATE_ERROR.msg,
                span.sid,
            )


@router.post("/protocol/get", status_code=status.HTTP_200_OK)
def get(flow_read: FlowRead, session: Session = Depends(get_session)) -> JSONResponse:
    """
    Get protocol flow by ID
    :param flow_read: Flow read request data
    :param session: Database session dependency
    :return: Flow data response
    """
    span = Span()
    m = Meter()
    with span.start(
        attributes={"flow_id": flow_read.flow_id},
    ) as current_span:
        try:
            flow = flow_service.get(flow_read.flow_id, session, current_span)
        except CustomException as err:
            m.in_error_count(err.code, span=current_span)
            current_span.record_exception(err)
            return response_error(err.code, err.message, span.sid)
        except Exception as e:
            m.in_error_count(CodeEnum.FLOW_GET_ERROR.code, span=current_span)
            current_span.record_exception(e)
            return response_error(
                CodeEnum.FLOW_GET_ERROR.code,
                CodeEnum.FLOW_GET_ERROR.msg,
                span.sid,
            )
        m.in_success_count()
        return response_success(flow.json(), span.sid)


@router.post("/protocol/update/{flow_id}", status_code=status.HTTP_200_OK)
def update(
    flow_id: str,
    flow: FlowUpdate,
    session: Session = Depends(get_session),
) -> JSONResponse:
    """
    Update protocol flow
    :param flow_id: Flow ID to update
    :param flow: Flow update data
    :param session: Database session dependency
    :return: Success response
    """
    span = Span()
    m = Meter()
    with span.start(
        attributes={"flow_id": flow_id},
    ) as current_span:
        try:
            current_span.add_info_event(f"update start: {flow_id}")
            del_flow_by_id(flow_id)
            update_content = json.dumps(flow.__dict__, ensure_ascii=False)
            current_span.add_info_event(f"update vo: {update_content}")
            sparkflow_protocol = flow.data
            if sparkflow_protocol:
                if isinstance(sparkflow_protocol, str):
                    sparkflow_protocol = json.loads(sparkflow_protocol)
                WorkflowEngineFactory.create_engine(
                    WorkflowDSL.parse_obj((flow.data or {}).get("data")), current_span
                )
            db_flow = session.query(Flow).filter_by(id=int(flow_id)).first()
            if not db_flow:
                raise CustomException(CodeEnum.FLOW_NOT_FOUND_ERROR)

            # Register app_id to App table for published workflows and bind in license table
            if db_flow.status > 0 or (flow.status is not None and flow.status > 0):
                if not db_flow.app_id:
                    raise CustomException(CodeEnum.FLOW_NO_APP_ID_ERROR)
                db_app = app_service.get_info(db_flow.app_id, session, current_span)
                license_service.bind(session, db_app, db_flow.group_id)
            flow_service.update(session, db_flow, flow, flow_id, current_span)
            m.in_success_count()
            return response_success(None, span.sid)
        except CustomException as err:
            current_span.record_exception(err)
            m.in_error_count(err.code, span=current_span)
            return response_error(err.code, err.message, span.sid)
        except Exception as e:
            current_span.record_exception(e)
            m.in_error_count(CodeEnum.PROTOCOL_UPDATE_ERROR.code, span=current_span)
            return response_error(
                CodeEnum.PROTOCOL_UPDATE_ERROR.code,
                CodeEnum.PROTOCOL_UPDATE_ERROR.msg,
                span.sid,
            )


@router.post("/protocol/delete", status_code=status.HTTP_200_OK)
def delete(
    flow: FlowRead,
    session: Session = Depends(get_session),
) -> JSONResponse:
    """
    Delete protocol flow
    :param flow: Flow read request data
    :param session: Database session dependency
    :return: Success response
    """
    span = Span()
    m = Meter()
    with span.start(
        attributes={"flow_id": flow.flow_id},
    ) as current_span:
        try:
            db_flow = (
                session.query(Flow)
                .filter_by(id=int(flow.flow_id), app_id=flow.app_id)
                .first()
            )
            if not db_flow:
                raise CustomException(CodeEnum.FLOW_NOT_FOUND_ERROR)
            session.delete(db_flow)
            session.commit()
            m.in_success_count()
            return response_success(None, span.sid)
        except Exception as e:
            current_span.record_exception(e)
            m.in_error_count(CodeEnum.PROTOCOL_DELETE_ERROR.code, span=current_span)
            return response_error(
                CodeEnum.PROTOCOL_DELETE_ERROR.code,
                CodeEnum.PROTOCOL_DELETE_ERROR.msg,
                span.sid,
            )


@router.post("/protocol/build/{flow_id}", response_model=None)
async def sparkflow_build(
    flow_id: str,
    session: Session = Depends(get_session),
    cache_service: "BaseCacheService" = Depends(get_cache_service),
) -> StreamingResponse:
    """
    Build protocol flow
    :param flow_id: Flow ID to build
    :param session: Database session dependency
    :param cache_service: Cache service dependency
    :return: Streaming response with build progress
    """
    m = Meter()
    span = Span()

    async def event_stream() -> AsyncGenerator[str, None]:
        with span.start(
            attributes={"flow_id": flow_id},
        ) as span_context:
            final_response = {"end_of_stream": True, "sid": span.sid}
            try:
                flow_service.build(flow_id, cache_service, session, span)
                m.in_success_count()
            except CustomException as err:
                span_context.record_exception(err)
                m.in_error_count(CodeEnum.OPEN_API_ERROR.code, span=span_context)
                yield Streaming.generate_data({"message": err.message})
            except Exception as err:
                span_context.record_exception(err)
                m.in_error_count(CodeEnum.PROTOCOL_BUILD_ERROR.code, span=span_context)
                yield Streaming.generate_data(
                    {"message": CodeEnum.PROTOCOL_BUILD_ERROR.msg}
                )
            finally:
                yield Streaming.generate_data(final_response)

    return StreamingResponse(
        event_stream(),
        media_type="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@router.get("/get_flow_info/{flow_id}", status_code=200)
def get_flow_info(
    x_consumer_username: Annotated[str, Header()],
    flow_id: str,
    session: Session = Depends(get_session),
) -> JSONResponse:
    """
    Get flow information for MCP input schema
    :param x_consumer_username: Consumer username from header
    :param flow_id: Flow ID to get info for
    :param session: Database session dependency
    :return: MCP input schema response
    """
    span = Span()
    app_alias_id = x_consumer_username
    m = Meter(app_id=app_alias_id)
    with span.start(
        attributes={"flow_id": flow_id},
    ) as current_span:
        try:
            published_flow = flow_service.get_latest_published_flow_by(
                flow_id, app_alias_id, session, current_span
            )
            mcp_input_schema = flow_service.gen_mcp_input_schema(published_flow)
        except CustomException as err:
            current_span.record_exception(err)
            m.in_error_count(err.code, span=current_span)
            return response_error(
                err.code,
                err.message,
                span.sid,
            )
        except Exception as e:
            m.in_error_count(CodeEnum.FLOW_GET_ERROR.code, span=current_span)
            current_span.record_exception(e)
            return response_error(
                CodeEnum.FLOW_GET_ERROR.code,
                CodeEnum.FLOW_GET_ERROR.msg,
                span.sid,
            )
        m.in_success_count()
        return response_success(mcp_input_schema, current_span.sid)


@router.post("/protocolcompare/save")
def save_comparisons(
    chat_input: SaveComparisonVo,
    session: Session = Depends(get_session),
) -> JSONResponse:
    """
    Save protocol comparison data
    :param chat_input: Comparison data to save
    :param session: Database session dependency
    :return: Success response
    """
    m = Meter()
    span = Span()
    with span.start(
        attributes={"flow_id": chat_input.flow_id},
    ) as span_context:
        try:
            db_flow = flow_service.get(chat_input.flow_id, session, span)
            comparison_data = Flow(
                group_id=db_flow.group_id,
                name=db_flow.name,
                data=chat_input.data,
                description=db_flow.description,
                status=db_flow.status,
                app_id=db_flow.app_id,
                source=db_flow.source,
                version=chat_input.version,
                tag=Tag.COMPARISON.value,
            )
            session.add(comparison_data)
            session.commit()
            m.in_success_count()
        except CustomException as e:
            span_context.record_exception(e)
            session.rollback()
            m.in_error_count(e.code, span=span_context)
            return response_error(e.code, e.message, span.sid)
        except Exception as e:
            span_context.record_exception(e)
            session.rollback()
            m.in_error_count(CodeEnum.PROTOCOL_CREATE_ERROR.code, span=span_context)
            return response_error(
                CodeEnum.PROTOCOL_CREATE_ERROR.code,
                CodeEnum.PROTOCOL_CREATE_ERROR.msg,
                span.sid,
            )

        return response_success(None, span.sid)


@router.delete("/protocol/compare/delete")
def delete_comparisons(
    delete_input: DeleteComparisonVo,
    session: Session = Depends(get_session),
) -> JSONResponse:
    """
    Delete protocol comparison data
    :param delete_input: Comparison deletion request data
    :param session: Database session dependency
    :return: Success response
    """
    m = Meter()
    span = Span()
    flow_id = delete_input.flow_id
    with span.start(
        attributes={"flow_id": flow_id},
    ) as span_context:
        try:
            db_flow = flow_service.get(delete_input.flow_id, session, span)
            session.query(Flow).filter(
                and_(
                    cast(ColumnElement[bool], Flow.group_id == db_flow.group_id),
                    cast(ColumnElement[bool], Flow.version == delete_input.version),
                )
            ).delete(synchronize_session=False)
            session.commit()
        except CustomException as e:
            span_context.record_exception(e)
            session.rollback()
            m.in_error_count(e.code, span=span_context)
            return response_error(e.code, e.message, span.sid)
        except Exception as e:
            span_context.record_exception(e)
            session.rollback()
            m.in_error_count(CodeEnum.PROTOCOL_DELETE_ERROR.code, span=span_context)
            return response_error(
                CodeEnum.PROTOCOL_DELETE_ERROR.code,
                CodeEnum.PROTOCOL_DELETE_ERROR.msg,
                span.sid,
            )
        m.in_success_count()
        return response_success(None, span.sid)
