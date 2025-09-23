from enum import Enum
from typing import Any, Dict, Optional

from pydantic import BaseModel, Field

from workflow.engine.callbacks.openai_types_sse import GenerateUsage
from workflow.exception.e import CustomException


class WorkflowNodeExecutionStatus(Enum):
    """
    Enumeration of workflow node execution statuses.

    This enum defines the possible states a workflow node can be in
    during its execution lifecycle.
    """

    RUNNING = "running"  # Node is currently executing
    SUCCEEDED = "succeeded"  # Node completed successfully
    FAILED = "failed"  # Node execution failed
    CANCELLED = "cancelled"  # Node execution was cancelled


class NodeRunOutputType(Enum):
    """
    Enumeration of node output data types.

    This enum defines the supported data types for node outputs
    in the workflow system.
    """

    STR = "string"  # String output type
    INT = "integer"  # Integer output type
    FLOAT = "number"  # Floating-point number output type
    BOOL = "boolean"  # Boolean output type


class NodeRunResult(BaseModel):
    """
    Result of a workflow node execution.

    This class encapsulates all the information about the execution
    of a workflow node, including inputs, outputs, status, and metadata.

    :param status: Current execution status of the node
    :param inputs: Input parameters passed to the node
    :param process_data: Intermediate processing data
    :param outputs: Output data produced by the node
    :param error_outputs: Error-related output data
    :param edge_source_handle: Source handle ID for nodes with multiple branches
    :param error: Error message if execution failed
    :param error_code: Numeric error code
    :param raw_output: Raw output from LLM nodes
    :param node_answer_content: Main answer content from the node
    :param node_answer_reasoning_content: Reasoning content from the node
    :param node_id: Unique identifier of the node
    :param alias_name: Human-readable name of the node
    :param node_type: Type/category of the node
    :param token_cost: Token usage information for LLM nodes
    """

    status: WorkflowNodeExecutionStatus = WorkflowNodeExecutionStatus.RUNNING
    inputs: Dict[str, Any] = Field(default_factory=dict)  # Node input parameters
    process_data: dict = Field(default_factory=dict)  # Intermediate processing data
    outputs: Dict[str, Any] = Field(default_factory=dict)  # Node output data
    error_outputs: Dict[str, Any] = Field(default_factory=dict)  # Error output data
    edge_source_handle: Optional[str] = (
        None  # Source handle ID for nodes with multiple branches
    )
    error: Optional[CustomException] = None  # Error message if status is failed
    raw_output: str = ""  # Raw LLM output
    node_answer_content: str = ""  # Main answer content
    node_answer_reasoning_content: str = ""  # Reasoning content
    node_id: str  # Node metadata - unique identifier
    alias_name: str  # Node metadata - human-readable name
    node_type: str  # Node metadata - type/category
    token_cost: GenerateUsage | None = None  # Token usage for LLM nodes

    class Config:
        arbitrary_types_allowed = True
