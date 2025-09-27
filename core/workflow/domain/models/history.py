"""
Workflow node history domain model.

This module defines the database model for tracking workflow node execution history,
including user interactions and node responses.
"""

from datetime import datetime
from typing import Optional

from sqlalchemy import BigInteger, Column
from sqlmodel import Field  # type: ignore
from workflow.domain.models.base import SQLModelSerializable


class History(SQLModelSerializable, table=True):  # type: ignore
    """
    Database model representing workflow node execution history.

    This model tracks the execution history of workflow nodes,
    including user questions, node responses, and execution metadata.

    :param id: Unique history record identifier (auto-increment)
    :param node_id: Identifier of the executed node
    :param uid: User identifier who triggered the execution
    :param chat_id: Optional chat session identifier
    :param raw_question: Original user question or input
    :param raw_answer: Raw response from the node
    :param create_time: Timestamp when the history record was created
    :param flow_id: Optional workflow identifier
    """

    __tablename__ = "workflow_node_history"
    id: Optional[int] = Field(
        sa_column=Column(BigInteger, primary_key=True, autoincrement=True)
    )
    node_id: str = Field(max_length=255, nullable=False)
    uid: str = Field(max_length=255, nullable=False)
    chat_id: Optional[str] = Field(max_length=255, default=None)
    raw_question: Optional[str] = None
    raw_answer: Optional[str] = None
    create_time: datetime = Field(default_factory=datetime.now)
    flow_id: Optional[str] = Field(max_length=255, default=None)
