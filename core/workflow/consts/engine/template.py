"""
Template-related constants.

This module defines template types and split types for template processing
in the workflow system.
"""

from enum import Enum


class TemplateType(Enum):
    """
    Template type enumeration.

    Defines different types of templates used in the workflow system.
    """

    NORMAL = 1
    REASONING = 2


class TemplateSplitType(Enum):
    """
    Template split type enumeration.

    When splitting templates, this enum marks the types of different parts:
    - 0: Constants that can be output directly
    - 1: Variables that need to wait for values
    - 2: LLM node output content in JSON format, needs to extract values from variable pool
    """

    CONSTS = 0
    VARIABLE = 1
    LLM_JSON = 2
