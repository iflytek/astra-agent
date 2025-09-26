from typing import Any, Dict, List, Literal, Union

from pydantic import BaseModel, Field

from workflow.engine.entities.retry_config import RetryConfig
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum


class NodeRef(BaseModel):
    """
    Node reference.
    :param node_id: ID of the node to reference
    :param name: Human-readable name for the node
    """

    nodeId: str = Field(..., min_length=1)
    name: str = Field(..., min_length=1)


LiteralValue = Union[str, int, bool, float, List[Any], Dict[str, Any]]
Content = Union[NodeRef, LiteralValue]


class Value(BaseModel):
    """
    Value.
    :param type: Type of the value
    :param content: Content of the value
    """

    type: Literal["ref", "literal"]
    content: Content


class InputSchema(BaseModel):
    """
    Input schema.
    :param type: Type of the input schema
    :param value: Value of the input schema
    """

    type: Literal["string", "boolean", "integer", "number", "array", "object"]
    value: Value


class InputItem(BaseModel):
    """
    Input item.
    :param id: ID of the input item
    :param name: Human-readable name for the input item
    :param input_schema: Input schema of the input item
    """

    id: str = Field(default="")
    name: str = Field(..., min_length=1)
    input_schema: InputSchema = Field(alias="schema")


class OutputItem(BaseModel):
    """ "
    Output item.
    :param id: ID of the output item
    :param name: Human-readable name for the output item
    :param output_schema: Output schema of the output item
    :param required: Whether the output item is required
    """

    id: str = Field(default="")
    name: str = Field(..., min_length=1)
    output_schema: Dict[str, Any] = Field(..., default_factory=dict, alias="schema")
    required: bool = Field(default=False)


class NodeMeta(BaseModel):
    """
    Node meta.
    :param node_type: Type of the node
    :param alias_name: Human-readable name for the node
    """

    nodeType: str = Field(description="Type of the node")
    aliasName: str = Field(description="Human-readable name for the node")


class NodeData(BaseModel):
    """
    Data structure representing a node's configuration and parameters.
    :param inputs: Input items of the node
    :param node_meta: Node meta of the node
    :param node_param: Node parameter of the node
    :param outputs: Output items of the node
    :param retry_config: Retry configuration of the node
    """

    inputs: List[InputItem] = Field(default_factory=list, min_length=0)
    nodeMeta: NodeMeta = Field(...)
    nodeParam: Dict[str, Any] = Field(default_factory=dict)
    outputs: List[OutputItem] = Field(default_factory=list, min_length=0)
    retryConfig: RetryConfig = Field(default_factory=RetryConfig)


class Node(BaseModel):
    """
    Represents a workflow node with its data and identifier.
    :param data: Data of the node
    :param id: ID of the node
    """

    data: NodeData = Field(...)
    id: str = Field(pattern="^.*::[0-9a-zA-Z-]+")

    def get_node_type(self) -> str:
        """
        Extract the node type from the node ID.

        :return: Node type string
        """
        return self.id.split(":")[0]


class Edge(BaseModel):
    """
    Represents a connection between two nodes in a workflow.
    :param source_node_id: ID of the source node
    :param target_node_id: ID of the target node
    :param source_handle: Source handle of the edge
    """

    sourceNodeId: str
    targetNodeId: str
    sourceHandle: str = ""


class WorkflowDSL(BaseModel):
    """
    Workflow DSL (Domain Specific Language) information.
    :param nodes: Nodes of the workflow
    :param edges: Edges of the workflow
    """

    # Node information
    nodes: List[Node]

    # Edge information
    edges: List[Edge]

    def check_nodes_exist(self, node_id: str) -> Node:
        """
        Check if a node exists in the workflow.

        :param node_id: ID of the node to check
        :return: Node object if found
        :raises CustomException: If node is not found
        """
        for node in self.nodes:
            if node.id == node_id:
                return node
        raise CustomException(
            CodeEnum.PROTOCOL_VALIDATION_ERROR, err_msg=f"Node {node_id} does not exist"
        )
