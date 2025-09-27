"""
Constants and enumerations for OpenAI LLM provider.
"""

from enum import Enum


class RespFormatEnum(Enum):
    """
    Enumeration for response format types.

    :cvar TEXT: Plain text format
    :cvar MARKDOWN: Markdown format
    :cvar JSON: JSON format
    """

    TEXT = 0
    MARKDOWN = 1
    JSON = 2
