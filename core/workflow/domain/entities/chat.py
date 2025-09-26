"""
Chat-related domain entities for workflow communication.

This module defines data models for chat interactions, including message roles,
content types, and request/response structures for workflow chat functionality.
"""

from enum import Enum
from typing import Any, Dict, List, Optional

from pydantic import BaseModel, Field


class RoleEnum(str, Enum):
    """Enumeration for chat message roles."""

    user = "user"
    assistant = "assistant"


class ContentTypeEnum(str, Enum):
    """Enumeration for different content types in chat messages."""

    image = "image"
    text = "text"
    audio = "audio"


class HistoryItem(BaseModel):
    """
    Represents a single item in chat history.

    :param role: The role of the message sender (user or assistant)
    :param content: The content of the message
    :param content_type: The type of content (defaults to text)
    """

    role: RoleEnum
    content: str
    content_type: Optional[ContentTypeEnum] = ContentTypeEnum.text


class ChatVo(BaseModel):
    """
    Value object for chat request parameters.

    :param flow_id: The workflow ID (required)
    :param uid: User ID, maximum 40 characters
    :param stream: Whether to use streaming response
    :param ext: Extended fields dictionary
    :param parameters: Parameters object (required)
    :param chat_id: Chat ID, maximum 128 characters
    :param history: List of chat history items
    :param version: Version number
    """

    flow_id: str = Field(description="Workflow ID")  # Required
    uid: str = Field("", max_length=40, description="User ID, maximum 40 characters")
    stream: bool = Field(True, description="Whether to use streaming")
    ext: Dict[str, Any] = Field({}, description="Extended fields")
    parameters: Dict[str, Any] = Field(..., description="Parameters object")  # Required
    chat_id: str = Field(
        "", max_length=128, description="Chat ID, maximum 128 characters"
    )
    history: List[HistoryItem] = Field([], description="History record list")
    version: str = Field("", description="Version number")

    def __str__(self) -> str:
        """String representation of the chat request."""
        return (
            f"flow_id: {self.flow_id}\n"
            f"uid: {self.uid}\n"
            f"parameters: {self.parameters}\n"
            f"stream: {self.stream}\n"
            f"ext: {self.ext}\n"
            f"chat_id: {self.chat_id}\n"
            f"history: {self.history}"
        )


class ResumeVo(BaseModel):
    """
    Value object for resume event.

    :param event_id: Event ID
    :param event_type: Event type (defaults to "resume")
    :param content: Event content
    """

    event_id: str
    """Event ID"""
    event_type: str = "resume"
    """Event type"""
    content: str
