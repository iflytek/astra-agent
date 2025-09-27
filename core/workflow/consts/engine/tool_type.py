"""
Tool type constants.

This module defines the different types of tools available
in the workflow system.
"""

from enum import Enum


class ToolType(Enum):
    """
    Tool type enumeration.

    Defines the different types of tools that can be used in workflow nodes.
    """

    TOOL = "tool"
    KNOWLEDGE = "knowledge"
