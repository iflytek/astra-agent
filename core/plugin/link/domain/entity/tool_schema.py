"""Tool database schema definitions for SparkLink plugin.

This module contains SQLModel definitions for tool-related database tables
and entities used by the SparkLink plugin system.
"""

import warnings
from datetime import datetime

import sqlalchemy as sa
from plugin.link.consts import const
from sqlmodel import BigInteger, Column, Field, SQLModel, String, Text

# Ignore schema name warnings
warnings.filterwarnings(
    "ignore",
    message='Field name "schema" in .* shadows an attribute in parent "SQLModel"',
    category=UserWarning,
)


class Tools(SQLModel, table=True):
    """
    Tool database table
    """

    __tablename__ = "tools_schema"
    id: int = Field(sa_column=Column(BigInteger, primary_key=True, autoincrement=True))
    app_id: str = Field(
        sa_column=Column(String(32), nullable=True, comment="Application ID")
    )
    tool_id: str = Field(
        sa_column=Column(String(32), nullable=True, unique=True, comment="Tool ID")
    )
    name: str = Field(sa_column=Column(String(128), nullable=True, comment="Tool name"))
    description: str = Field(
        sa_column=Column(String(512), nullable=True, comment="Tool description")
    )
    open_api_schema: str = Field(
        sa_column=Column(Text, nullable=True, comment="OpenAPI schema, JSON format")
    )
    create_at: datetime = Field(
        default_factory=datetime.now, sa_column=Column(sa.DateTime(timezone=True))
    )
    update_at: datetime = Field(default_factory=datetime.now)
    mcp_server_url: str = Field(
        sa_column=Column(String(255), nullable=True, comment="mcp_server_url")
    )
    schema: str = Field(
        sa_column=Column(Text, nullable=True, comment="Schema, JSON format")
    )
    version: str = Field(
        sa_column=Column(
            String(32), nullable=False, comment="Version number", default=const.DEF_VER
        )
    )
    is_deleted: int = Field(
        sa_column=Column(
            BigInteger, nullable=False, comment="Is deleted", default=const.DEF_DEL
        )
    )
