"""
Node debugging domain entities.

This module defines value objects for node debugging functionality,
including code execution parameters and debug response structures.
"""

from typing import Any

from pydantic import BaseModel, Field
from workflow.engine.callbacks.openai_types_sse import GenerateUsage
from workflow.engine.entities.workflow_dsl import WorkflowDSL


class Variable(BaseModel):
    """
    Represents a variable used in code execution.

    :param name: Variable name (minimum 1 character)
    :param content: Variable content of any type
    """

    name: str = Field(min_length=1)
    content: Any


class CodeRunVo(BaseModel):
    """
    Value object for code node execution parameters.

    :param code: User code to execute (minimum 1 character)
    :param variables: List of function arguments
    :param app_id: Application ID (minimum 1 character)
    :param uid: User ID (minimum 1 character)
    :param flow_id: Workflow ID (defaults to empty string)
    """

    # User code
    code: str = Field(min_length=1)
    # Function argument list
    variables: list[Variable]
    app_id: str = Field(min_length=1)
    uid: str = Field(min_length=1)
    flow_id: str = ""


class NodeDebugVo(BaseModel):
    """
    Value object for node testing.

    :param id: Workflow ID (minimum 1 character)
    :param name: Workflow name (minimum 1 character)
    :param description: Workflow description (minimum 1 character)
    :param data: Node DSL data
    """

    # Workflow ID
    id: str = Field(min_length=1)
    # Workflow name
    name: str = Field(min_length=1)
    # Workflow description
    description: str = Field(default="")
    # Node DSL
    data: WorkflowDSL


class NodeDebugRespVo(BaseModel):
    """
    Response value object for node debugging.

    :param node_id: Node identifier
    :param alias_name: Node alias name
    :param node_type: Type of the node
    :param input: Input data
    :param raw_output: Raw output data
    :param output: Processed output data
    :param node_exec_cost: Node execution cost
    :param token_cost: Token usage cost information
    """

    node_id: str
    alias_name: str
    node_type: str
    input: str
    raw_output: str
    output: str
    node_exec_cost: str
    token_cost: GenerateUsage
