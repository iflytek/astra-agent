"""
Workflow and flow-related constants.

This module defines constants and enumerations for workflow execution,
node statuses, error handling, and flow states.
"""

from enum import Enum


class FlowStatus(Enum):
    """
    Flow status enumeration.

    Defines the publication status of workflows.
    """

    DRAFT = 0
    PUBLISHED = 1
