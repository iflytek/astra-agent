from sqlmodel import Session  # type: ignore

from workflow.consts.tenant_publish_matrix import Platform, TenantPublishMatrix
from workflow.domain.entities.flow import AuthInput
from workflow.domain.models.flow import Flow
from workflow.domain.models.license import License
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span
from workflow.service import app_service


def handle(
    session: Session, tenant_app_id: str, auth_input: AuthInput, span: Span
) -> None:
    """
    Handle authentication and authorization for workflow binding.

    This function validates tenant permissions, checks workflow publish status,
    and registers the binding relationship in the license table.

    :param session: Database session for data operations
    :param tenant_app_id: Tenant application ID for validation
    :param auth_input: Authentication input containing app_id and flow_id
    :param span: Distributed tracing span for monitoring
    :return: None
    :raises CustomException: When tenant not found, flow not found,
            or flow not published
    """
    user_app_id = auth_input.app_id

    # Validate tenant application exists and is a tenant
    db_tenant_app = app_service.get_info(tenant_app_id, session, span)
    if not db_tenant_app.is_tenant:
        span.add_info_event(f"Tenant app ID: {tenant_app_id}")
        raise CustomException(
            CodeEnum.APP_TENANT_NOT_FOUND_ERROR,
            err_msg=f"{tenant_app_id} is not a tenant",
        )

    # Get user application information
    db_app = app_service.get_info(user_app_id, session, span)

    # Validate workflow exists
    db_flow = session.query(Flow).filter_by(id=auth_input.flow_id).first()
    if not db_flow:
        span.add_info_event(f"Flow ID: {auth_input.flow_id}")
        raise CustomException(CodeEnum.FLOW_NOT_FOUND_ERROR)

    group_id = db_flow.group_id
    release_status = (
        db_flow.release_status
    )  # Current workflow publish permissions across platforms
    rs = TenantPublishMatrix(
        db_app.source
    ).get_publish  # Current platform publish permission value

    span.add_info_event(f"Group ID: {group_id}")
    span.add_info_event(
        f"Current workflow publish permissions across platforms: {release_status}"
    )
    span.add_info_event(f"Current platform publish permission value: {rs}")

    # Check if workflow is published or not taken off from all platforms
    # Bottom line: Workflow should not be bindable if unpublished
    # or taken off from all three platforms
    if (release_status == 0) or (
        (release_status & TenantPublishMatrix(Platform.XINGCHEN).get_take_off)
        and (release_status & TenantPublishMatrix(Platform.KAI_FANG).get_take_off)
        and (release_status & TenantPublishMatrix(Platform.AI_UI).get_take_off)
    ):
        raise CustomException(
            CodeEnum.FLOW_NOT_PUBLISH_ERROR,
        )

    # Register group_id and app_id binding in license table
    db_license = (
        session.query(License).filter_by(app_id=db_app.id, group_id=group_id).first()
    )
    if not db_license:
        db_license = License(app_id=db_app.id, group_id=db_flow.group_id)
        session.add(db_license)
    return
