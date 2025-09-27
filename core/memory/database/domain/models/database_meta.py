"""Module defining the DatabaseMeta model for database metadata management."""

from datetime import datetime
from typing import Optional

from memory.database.domain.models.base import SQLModelSerializable
from memory.database.utils.snowfake import get_id
from sqlalchemy import BigInteger, Column
from sqlmodel import Field


class DatabaseMeta(
    SQLModelSerializable, table=True  # type: ignore[call-arg]
):  # pylint: disable=too-few-public-methods
    """Database metadata model representing database information and ownership.

    Attributes:
        id: Primary key identifier (auto-generated)
        uid: Unique identifier for the database
        name: Name of the database
        description: Optional description of the database
        space_id: Associated workspace ID
        create_at: Timestamp of creation
        update_at: Timestamp of last update
        create_by: Creator identifier
        update_by: Last updater identifier
    """

    __tablename__ = "database_meta"
    __table_args__ = {"schema": "sparkdb_manager"}
    id: int = Field(
        default_factory=get_id, sa_column=Column(BigInteger, primary_key=True)
    )
    uid: str = Field(default="", nullable=False, index=True)
    name: str = Field(default="", index=True, nullable=False)
    description: Optional[str] = Field(default="")
    space_id: str = Field(default="", index=True)
    create_at: datetime = Field(default_factory=datetime.now)
    update_at: datetime = Field(default_factory=datetime.now)
    create_by: Optional[str] = Field(default="")
    update_by: Optional[str] = Field(default="")
