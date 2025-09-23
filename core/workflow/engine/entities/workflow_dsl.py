from typing import Any, Dict, List

from pydantic import BaseModel, Field

from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum


class NodeData(BaseModel):
    """
    Data structure representing a node's configuration and parameters.
    """

    inputs: List[Any] = Field(default_factory=list)
    nodeMeta: Dict[str, Any] = Field(default_factory=dict)
    nodeParam: Dict[str, Any] = Field(default_factory=dict)
    outputs: List[Dict[str, Any]] = Field(default_factory=list)
    retryConfig: Dict[str, Any] = Field(default_factory=dict)


class Node(BaseModel):
    """
    Represents a workflow node with its data and identifier.
    """

    data: NodeData
    id: str

    def get_node_type(self) -> str:
        """
        Extract the node type from the node ID.

        :return: Node type string
        """
        return self.id.split(":")[0]


class Edge(BaseModel):
    """
    Represents a connection between two nodes in a workflow.
    """

    sourceNodeId: str
    targetNodeId: str
    sourceHandle: str = ""


class WorkflowDSL(BaseModel):
    """
    Workflow DSL (Domain Specific Language) information.
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
            CodeEnum.ProtocolValidationErr, err_msg=f"Node {node_id} does not exist"
        )
