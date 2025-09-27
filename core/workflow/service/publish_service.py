from typing import Any

from sqlalchemy import text
from sqlmodel import Session  # type: ignore

from workflow.consts.tenant_publish_matrix import (
    RELEASE_MAPPING,
    SOURCE_MAPPING,
    ReleaseStatus,
    TenantPublishMatrix,
)
from workflow.domain.entities.flow import PublishInput
from workflow.domain.models.ai_app import App
from workflow.domain.models.flow import Flow
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span
from workflow.service import app_service


def handle(
    session: Session, tenant_app_id: str, publish_input: PublishInput, span: Span
) -> None:
    """
    Handle workflow publishing operations for tenant applications.

    This function orchestrates the complete workflow publishing process, including
    validation of tenant permissions, platform authorization, release status management,
    and version handling. It ensures that only authorized tenants can publish workflows
    to their permitted platforms.

    :param session: Database session for data operations
    :param tenant_app_id: Unique identifier for the tenant application
    :param publish_input: Publishing configuration and data
    :param span: Tracing span for observability and debugging
    """
    # Retrieve and validate tenant application information
    db_app = app_service.get_info(tenant_app_id, session, span)
    if db_app.is_tenant != 1:
        span.add_info_event(f"Tenant app ID: {tenant_app_id}")
        raise CustomException(CodeEnum.APP_TENANT_NOT_FOUND_ERROR)

    # Determine target platform (use input platform or default to app source)
    plat = publish_input.plat if publish_input.plat else db_app.source
    span.add_info_event(f"Platform: {plat}")

    # Retrieve the workflow to be published
    db_flow = _get_flow(session, publish_input.flow_id, span)

    # Validate tenant permissions for the target platform
    _check_permissions(db_app, db_flow, plat, span)

    # Get and validate release status for the platform
    release_status = _get_release_status(publish_input.release_status, plat, span)

    # Update workflow release status based on operation type
    _update_flow_release_status(db_flow, release_status, publish_input, plat)

    # Update workflow data if publishing
    _update_flow_data(db_flow, publish_input)

    # Handle version management for published workflows
    _handle_version(session, db_flow, publish_input)
    return


def _check_permissions(db_app: App, db_flow: Flow, plat: int, span: Span) -> None:
    """
    Validate tenant application permissions for platform publishing.

    Checks if the tenant application has the necessary permissions to publish
    workflows to the target platform and if the workflow's source platform
    is authorized for the tenant.

    :param db_app: The tenant application object
    :param db_flow: The workflow object to be published
    :param plat: Target platform identifier
    :param span: Tracing span for observability
    :raises CustomException: If tenant lacks platform publishing permissions
    """
    # Check if tenant has permission to publish to target platform
    if db_app.plat_release_auth & plat == 0:
        span.add_info_event(f"App platform release auth: {db_app.plat_release_auth}")
        raise CustomException(
            CodeEnum.APP_TENANT_PLATFORM_UNAUTHORIZED_ERROR,
            err_msg=f"Current app_id does not have permission "
            f"to publish to {SOURCE_MAPPING[plat]}",
        )
    # Check if tenant has permission for workflow's source platform
    if db_app.plat_release_auth & db_flow.source == 0:
        span.add_info_event(f"App platform release auth: {db_app.plat_release_auth}")
        raise CustomException(
            CodeEnum.APP_TENANT_PLATFORM_UNAUTHORIZED_ERROR,
            err_msg=f"Current flow is on platform {SOURCE_MAPPING[db_flow.source]}, "
            f"but current app_id does not have permission for "
            f"{SOURCE_MAPPING[db_flow.source]}",
        )


def _get_flow(session: Session, flow_id: str, span: Span) -> Any:
    """
    Retrieve workflow by ID from the database.

    Queries the database for a workflow with the specified ID and validates
    that it exists before returning it.

    :param session: Database session for query operations
    :param flow_id: Unique identifier of the workflow to retrieve
    :param span: Tracing span for observability
    :return: The workflow object if found
    :raises CustomException: If workflow with the given ID is not found
    """
    db_flow = session.query(Flow).filter_by(id=flow_id).first()
    if not db_flow:
        span.add_info_event(f"Flow ID: {flow_id}")
        raise CustomException(CodeEnum.FLOW_NOT_FOUND_ERROR)
    return db_flow


def _get_release_status(release_status: int, plat: int, span: Span) -> int:
    """
    Get and validate release status for the specified platform.

    Uses the tenant publish matrix to determine the appropriate release status
    for the given platform and validates that the operation is supported.

    :param release_status: The requested release status operation
    :param plat: Target platform identifier
    :param span: Tracing span for observability
    :return: The validated release status for the platform
    :raises CustomException: If the release operation is not supported for the platform
    """
    rs = TenantPublishMatrix(plat).get_release_status(release_status)
    span.add_info_event(f"Release status: {rs}")
    if rs == -1:
        raise CustomException(
            CodeEnum.APP_PLAT_NOT_RELEASE_OP_ERROR,
            err_msg=f"Error: {SOURCE_MAPPING[plat]} "
            f"does not support {RELEASE_MAPPING[release_status]} operation",
        )
    return rs


def _update_flow_release_status(
    db_flow: Flow, release_status: int, publish_input: PublishInput, plat: int
) -> None:
    """
    Update workflow release status based on the operation type.

    Handles different release operations (take off, publish, publish API) by
    updating the workflow's release status flags appropriately and clearing
    conflicting statuses.

    :param db_flow: The workflow object to update
    :param release_status: The new release status to apply
    :param publish_input: Publishing input containing operation details
    :param plat: Target platform identifier
    """
    # Handle take off operation - remove publish statuses
    if publish_input.release_status == ReleaseStatus.TAKE_OFF.value:
        _update_release_status_for_take_off(db_flow, plat)
    # Handle publish operations - remove take off status
    elif publish_input.release_status in [
        ReleaseStatus.PUBLISH.value,
        ReleaseStatus.PUBLISH_API.value,
    ]:
        _update_release_status_for_publish(db_flow, plat)
    # Apply the new release status using bitwise OR
    db_flow.release_status |= release_status


def _update_release_status_for_take_off(db_flow: Flow, plat: int) -> None:
    """
    Update release status when taking off a workflow from a platform.

    Removes publish and publish API status flags from the workflow's release status
    when taking it off from the specified platform.

    :param db_flow: The workflow object to update
    :param plat: Platform identifier for the take off operation
    """
    matrix = TenantPublishMatrix(plat)
    # Remove publish status if it exists
    if matrix.get_publish != -1 and db_flow.release_status & matrix.get_publish:
        db_flow.release_status -= matrix.get_publish
    # Remove publish API status if it exists
    if matrix.get_publish_api != -1 and db_flow.release_status & matrix.get_publish_api:
        db_flow.release_status -= matrix.get_publish_api


def _update_release_status_for_publish(db_flow: Flow, plat: int) -> None:
    """
    Update release status when publishing a workflow to a platform.

    Removes take off status flag from the workflow's release status when
    publishing it to the specified platform.

    :param db_flow: The workflow object to update
    :param plat: Platform identifier for the publish operation
    """
    matrix = TenantPublishMatrix(plat)
    # Remove take off status if it exists
    if matrix.get_take_off != -1 and db_flow.release_status & matrix.get_take_off:
        db_flow.release_status -= matrix.get_take_off


def _update_flow_data(db_flow: Flow, publish_input: PublishInput) -> None:
    """
    Update workflow data based on the publishing operation.

    Updates the workflow's data and release_data fields based on the operation type.
    For take off operations, only updates release_data. For publish operations,
    updates both data and release_data with the new workflow data.

    :param db_flow: The workflow object to update
    :param publish_input: Publishing input containing new data and operation type
    """
    # For non-take-off operations, sync release_data with current data
    if publish_input.release_status != ReleaseStatus.TAKE_OFF:
        db_flow.release_data = db_flow.data
    # For publish operations with new data, update both data and release_data
    if (
        publish_input.release_status
        in [ReleaseStatus.PUBLISH.value, ReleaseStatus.PUBLISH_API.value]
        and publish_input.data
    ):
        db_flow.data = publish_input.data.model_dump()
        db_flow.release_data = publish_input.data.model_dump()


def _handle_version(
    session: Session, db_flow: Flow, publish_input: PublishInput
) -> None:
    """
    Handle version management for workflow publishing.

    For publish operations with a specified version, either creates a new versioned
    workflow or updates an existing one. Also updates the release status for all
    workflows in the same group to maintain consistency.

    :param session: Database session for data operations
    :param db_flow: The workflow object being published
    :param publish_input: Publishing input containing version information
    """
    # Handle version creation/update for publish operations
    if (
        publish_input.release_status
        in [
            ReleaseStatus.PUBLISH.value,
            ReleaseStatus.PUBLISH_API.value,
        ]
        and publish_input.version
    ):
        # Check if a workflow with the same group_id and version already exists
        group_id_version_data = (
            session.query(Flow)
            .filter_by(group_id=db_flow.group_id, version=publish_input.version)
            .first()
        )
        if not group_id_version_data:
            # Create a new versioned workflow backup
            db_flow_backup = Flow(
                group_id=db_flow.group_id,
                name=db_flow.name,
                data=db_flow.data,
                release_data=db_flow.release_data,
                description=db_flow.description,
                version=publish_input.version,
                status=db_flow.status,
                release_status=db_flow.release_status,
                app_id=db_flow.app_id,
                source=db_flow.source,
            )
            session.add(db_flow_backup)
            # Mark original workflow as non-versioned
            db_flow.version = "-1"
        else:
            # Update existing versioned workflow with current data
            group_id_version_data.release_status = db_flow.release_status
            group_id_version_data.data = db_flow.data
            group_id_version_data.release_data = db_flow.release_data

    # Update release_status for all workflows with the same group_id
    session.execute(
        text(
            """
                UPDATE flow
                SET release_status = :release_status
                WHERE group_id = :group_id
            """
        ),
        {
            "release_status": db_flow.release_status,
            "group_id": db_flow.group_id,
        },
    )
