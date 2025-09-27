"""
Publish and authentication API endpoints for workflow system.

This module provides API endpoints for publishing workflows and managing
authentication bindings between applications and workflows.
"""

from typing import Annotated

from fastapi import APIRouter, Depends, Header
from fastapi.responses import JSONResponse
from sqlmodel import Session  # type: ignore
from workflow.cache.flow import del_flow_by_flow_id_latest_version, del_flow_by_id
from workflow.domain.entities.flow import AuthInput, PublishInput
from workflow.domain.entities.response import response_error, response_success
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.middleware.getters import get_session
from workflow.extensions.otlp.metric.meter import Meter
from workflow.extensions.otlp.trace.span import Span
from workflow.service import auth_service, publish_service

publish_auth_router = APIRouter(prefix="/v1", tags=["Flows"])


@publish_auth_router.post("/publish")
def publish(
    x_consumer_username: Annotated[str, Header()],
    publish_input: PublishInput,
    db_session: Session = Depends(get_session),
) -> JSONResponse:
    """
    Publish a workflow to make it available for use.

    :param x_consumer_username: Consumer username from header
    :param publish_input: Publish request data
    :param db_session: Database session dependency
    :return: Success response
    """
    tenant_app_id = x_consumer_username
    m = Meter(tenant_app_id)
    span = Span(app_id=tenant_app_id)

    with span.start(
        attributes={"flow_id": publish_input.flow_id},
    ) as span_context:
        span_context.add_info_event(f"user input: {publish_input.dict()}")

        # Delete flow protocol from Redis cache
        del_flow_by_id(publish_input.flow_id)
        del_flow_by_flow_id_latest_version(publish_input.flow_id)

        try:
            publish_service.handle(
                db_session, tenant_app_id, publish_input, span_context
            )
            db_session.commit()
        except CustomException as err:
            span_context.record_exception(err)
            db_session.rollback()
            m.in_error_count(err.code, span=span_context)
            return response_error(
                code=err.code, message=err.message, sid=span_context.sid
            )
        except Exception as err:
            span_context.record_exception(err)
            db_session.rollback()
            m.in_error_count(CodeEnum.FLOW_PUBLISH_ERROR.code, span=span_context)
            return response_error(
                code=CodeEnum.FLOW_PUBLISH_ERROR.code,
                message=f"{CodeEnum.FLOW_PUBLISH_ERROR.msg}, Error details: {err}",
                sid=span_context.sid,
            )
        m.in_success_count()
        return response_success(sid=span_context.sid)


@publish_auth_router.post("/auth")
def auth(
    x_consumer_username: Annotated[str, Header()],
    auth_input: AuthInput,
    db_session: Session = Depends(get_session),
) -> JSONResponse:
    """
    Authenticate and bind application to workflow.

    :param x_consumer_username: Consumer username from header
    :param auth_input: Authentication request data
    :param db_session: Database session dependency
    :return: Success response
    """
    tenant_app_id = x_consumer_username
    m = Meter(tenant_app_id)
    span = Span(app_id=tenant_app_id)

    with span.start(
        attributes={"flow_id": auth_input.flow_id},
    ) as span_context:
        span_context.add_info_event(f"user input: {auth_input.dict()}")

        try:
            auth_service.handle(db_session, tenant_app_id, auth_input, span_context)
            db_session.commit()
        except CustomException as err:
            span_context.record_exception(err)
            db_session.rollback()
            m.in_error_count(err.code, span=span_context)
            return response_error(
                code=err.code, message=err.message, sid=span_context.sid
            )
        except Exception as err:
            span_context.record_exception(err)
            db_session.rollback()
            m.in_error_count(CodeEnum.APP_FLOW_AUTH_BOND_ERROR.code, span=span_context)
            return response_error(
                code=CodeEnum.APP_FLOW_AUTH_BOND_ERROR.code,
                message=f"{CodeEnum.APP_FLOW_AUTH_BOND_ERROR.msg}, Error details: {err}",
                sid=span_context.sid,
            )
        m.in_success_count()
        return response_success(sid=span_context.sid)
