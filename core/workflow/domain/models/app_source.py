"""
Application source domain model.

This module defines the database model for application sources,
tracking the origin and metadata of applications in the system.
"""

from datetime import datetime

from sqlmodel import Field, SQLModel  # type: ignore
from workflow.utils.snowfake import get_id


class AppSource(SQLModel, table=True):  # type: ignore
    """
    Database model representing an application source.

    This model tracks the source information for applications,
    including source identifiers and metadata.

    :param id: Unique source identifier (auto-generated)
    :param source: Source type identifier
    :param source_id: External source identifier
    :param description: Source description
    :param create_at: Creation timestamp
    :param update_at: Last update timestamp
    """

    __tablename__ = "app_source"

    id: int = Field(default_factory=get_id, primary_key=True, unique=True)
    source: int = Field(default=0)
    source_id: str = Field(default="")
    description: str = Field(default="")
    create_at: datetime = Field(default_factory=datetime.now)
    update_at: datetime = Field(default_factory=datetime.now)
