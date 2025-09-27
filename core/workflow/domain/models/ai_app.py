"""
AI Application domain model.

This module defines the database model for AI applications,
including authentication, configuration, and metadata.
"""

from datetime import datetime

from sqlmodel import Field, SQLModel  # type: ignore
from workflow.utils.snowfake import get_id


class App(SQLModel, table=True):  # type: ignore
    """
    Database model representing an AI application.

    This model stores application configuration, authentication credentials,
    and metadata for AI applications in the workflow system.

    :param id: Unique application identifier (auto-generated)
    :param name: Application name
    :param alias_id: Application alias identifier
    :param api_key: API key for authentication
    :param api_secret: API secret for authentication
    :param description: Application description
    :param is_tenant: Whether this is a tenant application (0=no, 1=yes)
    :param source: Application source identifier
    :param actual_source: Actual source identifier
    :param plat_release_auth: Platform release authorization level
    :param status: Application status (1=active, 0=inactive)
    :param audit_policy: Audit policy configuration
    :param create_by: User ID who created the application
    :param update_by: User ID who last updated the application
    :param create_at: Creation timestamp
    :param update_at: Last update timestamp
    """

    id: int = Field(default_factory=get_id, primary_key=True)
    name: str = Field(default="")
    alias_id: str = Field(default="")
    api_key: str = Field(default="")
    api_secret: str = Field(default="")
    description: str = Field(default="")
    is_tenant: int = Field(default=0)
    source: int = Field(default=0)
    actual_source: int = Field(default=0)
    plat_release_auth: int = Field(default=0)
    status: int = Field(default=1)
    audit_policy: int = Field(default=0)
    create_by: int = Field(default=None)
    update_by: int = Field(default=None)
    create_at: datetime = Field(default_factory=datetime.now)
    update_at: datetime = Field(default_factory=datetime.now)
