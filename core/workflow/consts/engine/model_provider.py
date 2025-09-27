"""
Model provider constants.

This module defines the available model providers for LLM operations
in the workflow system.
"""

from enum import Enum


class ModelProviderEnum(Enum):
    """
    Model provider enumeration.

    Defines the available model providers for large language model operations.
    """

    XINGHUO = "xinghuo"
    OPENAI = "openai"
