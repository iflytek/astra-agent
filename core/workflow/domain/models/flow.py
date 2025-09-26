"""
Workflow domain model.

This module defines the database model for workflows,
including workflow data, metadata, and versioning information.
"""

from datetime import datetime
from typing import Dict

from sqlalchemy import JSON, Column
from sqlmodel import Field  # type: ignore

from workflow.domain.models.base import SQLModelSerializable
from workflow.utils.snowfake import get_id


class Flow(SQLModelSerializable, table=True):  # type: ignore
    """
    Database model representing a workflow.

    This model stores workflow definitions, including the workflow structure,
    metadata, versioning information, and release status.

    :param id: Unique workflow identifier (auto-generated)
    :param group_id: Group identifier for workflow organization
    :param name: Workflow name
    :param data: Workflow structure data (stored as JSON)
    :param release_data: Released workflow data (stored as JSON)
    :param description: Workflow description
    :param version: Workflow version string
    :param status: Workflow status (0=inactive, 1=active)
    :param release_status: Release status (0=not released, 1=released)
    :param app_id: Associated application identifier
    :param source: Source identifier for the workflow
    :param create_at: Creation timestamp
    :param tag: Workflow tag for categorization
    :param update_at: Last update timestamp
    """

    id: int = Field(default_factory=get_id, primary_key=True, unique=True)
    group_id: int = Field(default=0, index=True, unique=True)
    name: str = Field(default="", index=True)
    data: Dict = Field(default_factory=dict, sa_column=Column(JSON))
    release_data: Dict = Field(default_factory=dict, sa_column=Column(JSON))
    description: str = Field(default="", index=True)
    version: str = Field(default="", index=True)
    status: int = Field(default=0)
    release_status: int = Field(default=0)
    app_id: str = Field(default="")
    source: int = Field(default=0)
    create_at: datetime = Field(default_factory=datetime.now)
    tag: int = Field(default=0)
    update_at: datetime = Field(default_factory=datetime.now)
