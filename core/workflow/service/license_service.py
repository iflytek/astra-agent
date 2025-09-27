"""License service module for managing application license bindings.

This module provides functionality to bind applications to specific groups
through license records in the database.
"""

from sqlmodel import Session  # type: ignore

from workflow.domain.models.ai_app import App
from workflow.domain.models.license import License


def bind(session: Session, db_app: App, group_id: int) -> None:
    """Bind an application to a specific group through a license record.

    Creates a new license record if one doesn't exist for the given
    application and group combination.

    :param session: Database session for performing operations
    :param db_app: Application instance to bind
    :param group_id: Group identifier to bind the application to
    """
    # Check if license already exists for this app and group
    db_license = (
        session.query(License).filter_by(app_id=db_app.id, group_id=group_id).first()
    )

    # Create new license record if none exists
    if not db_license:
        db_license = License(app_id=db_app.id, group_id=group_id)
        session.add(db_license)
