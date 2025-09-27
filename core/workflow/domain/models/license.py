"""
License domain model.

This module defines the database model for application licenses,
managing license assignments and status for applications and groups.
"""

from datetime import datetime

from sqlmodel import Field, SQLModel  # type: ignore
from workflow.utils.snowfake import get_id


class License(SQLModel, table=True):  # type: ignore
    """
    Database model representing an application license.

    This model manages license assignments between applications and groups,
    tracking license status and metadata.

    :param id: Unique license identifier (auto-generated)
    :param app_id: Associated application identifier
    :param group_id: Associated group identifier
    :param status: License status (1=active, 0=inactive)
    :param create_at: Creation timestamp
    :param update_at: Last update timestamp
    """

    __tablename__ = "license"

    id: int = Field(default_factory=get_id, primary_key=True, unique=True)
    app_id: int = Field()
    group_id: int = Field()
    status: int = Field(default=1, index=True)
    create_at: datetime = Field(default_factory=datetime.now)
    update_at: datetime = Field(default_factory=datetime.now)
