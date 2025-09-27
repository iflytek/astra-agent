from asyncio import Event
from typing import Dict, List

from pydantic import BaseModel, Field
from workflow.engine.entities.workflow_dsl import Node, WorkflowDSL


class SimplePath(BaseModel):
    """
    Represents a simple execution path in the workflow.
    A simple path is a linear sequence of nodes that can be executed in order.
    """

    class Config:
        arbitrary_types_allowed = True

    node_id_list: list[str]
    # Records the number of nodes before each node in this simple path
    every_node_index: Dict[str, int]
    # Currently executing node in this simple path
    inactive: Event = Field(default_factory=Event)


class Chains(BaseModel):
    """
    Represents the execution chains of a workflow.
    Contains both master chains and iteration chains for complex workflow execution.
    """

    # Main execution chains
    master_chains: List[SimplePath] = []
    # Internal chains for iteration nodes, key: iteration node ID, value: chains
    iteration_chains: Dict[str, "Chains"] = {}
    workflow_schema: WorkflowDSL

    # Edge mapping relationships
    edge_dict: Dict[str, List[str]] = {}

    class Config:
        arbitrary_types_allowed = True  # Allow arbitrary types

    def get_all_simple_paths_node_cnt(self) -> int:
        """
        Get the total number of nodes in all simple paths.

        :return: Total count of nodes across all simple paths
        """
        cnt = 0
        for one_simple_chain in self.master_chains:
            cnt += len(one_simple_chain.node_id_list)
        return cnt

    def get_node_chains(self, node_id: str) -> List[SimplePath]:
        """
        Get all simple paths that contain the specified node.

        :param node_id: The ID of the node to search for
        :return: List of simple paths containing the node
        """
        node_chains = []
        for simple_path in self.master_chains:
            if node_id in simple_path.node_id_list:
                node_chains.append(simple_path)
        return node_chains

    def get_node_chains_with_node_id(self, node_id: str) -> List[SimplePath]:
        """
        Get all simple paths containing the specified node from iteration chains.

        :param node_id: The ID of the node to search for
        :return: List of simple paths containing the node from iteration chains
        """
        node_chains = []
        for iter_node_id, chains in self.iteration_chains.items():
            node_chain = chains.get_node_chains(node_id)
            node_chains.extend(node_chain)
        return node_chains

    def get_branch_chains(self, node_id: str, branch_node_id: str) -> List[SimplePath]:
        """
        Get simple paths that contain a specific branch from one node to another.

        :param node_id: The source node ID
        :param branch_node_id: The target branch node ID
        :return: List of simple paths containing the specified branch
        """
        node_chains = []
        for simple_path in self.master_chains:
            for i in range(len(simple_path.node_id_list) - 1):
                pre_node_id, cur_node_id = (
                    simple_path.node_id_list[i],
                    simple_path.node_id_list[i + 1],
                )
                if pre_node_id == node_id and cur_node_id == branch_node_id:
                    node_chains.append(simple_path)
        return node_chains

    def _deal_edges(self) -> tuple[str, str, Dict[str, List[str]], Dict[str, str]]:
        """
        Process the edges of the workflow graph.

        :return: A tuple containing:
                - start_node_id: Start node ID
                - end_node_id: End node ID
                - edge_dict: Edge dictionary mapping source to target nodes
                - iteration_dict: Iteration node dictionary
        """

        edge_dict: Dict[str, List[str]] = {}
        node_dict: Dict[str, Node] = {}
        iteration_dict: Dict[str, str] = {}

        start_node_id = ""
        end_node_id = ""

        for node in self.workflow_schema.nodes:
            node_dict[node.id] = node

        for edge in self.workflow_schema.edges:

            source_node_id = edge.sourceNodeId
            target_node_id = edge.targetNodeId
            if source_node_id not in edge_dict:
                edge_dict[source_node_id] = []
            if target_node_id not in edge_dict[source_node_id]:
                edge_dict[source_node_id].append(target_node_id)

            source_node_id_prefix = source_node_id.split("::")[0]
            target_node_id_prefix = target_node_id.split("::")[0]
            if source_node_id_prefix == "node-start":
                start_node_id = source_node_id
            if target_node_id_prefix == "node-end":
                end_node_id = target_node_id

            if source_node_id_prefix == "iteration":
                iter_node: Node | None = node_dict.get(source_node_id)
                if iter_node is None:
                    raise ValueError(f"{source_node_id} node is not exist")
                start_id = iter_node.data.nodeParam.get("IterationStartNodeId")
                iteration_dict[source_node_id] = str(start_id or "")

        return start_node_id, end_node_id, edge_dict, iteration_dict

    def _get_next_node(
        self, node_id: str, edge_dict: Dict[str, List[str]]
    ) -> List[List[str]]:
        """
        Recursively get all possible paths starting from the given node.

        :param node_id: The starting node ID
        :param edge_dict: Dictionary mapping nodes to their next nodes
        :return: List of all possible paths from the starting node
        """
        result: List[List[str]] = []

        next_node_ids = edge_dict.get(node_id, [])
        for next_node_id in next_node_ids:
            one = [next_node_id]
            next_result = self._get_next_node(next_node_id, edge_dict)

            if len(next_result) > 0:
                for nr in next_result:
                    oo = one + nr
                    result.append(oo)
            else:
                result.append(one)
        return result

    def _get_every_node_index(self, node_id_list: List[str]) -> Dict[str, int]:
        """
        Get the index of each node in the node list.

        :param node_id_list: List of node IDs
        :return: Dictionary mapping node ID to its index in the list
        """
        node_id_pre_node_cnt: Dict[str, int] = {}
        for node_id in node_id_list:
            node_id_pre_node_cnt[node_id] = len(node_id_pre_node_cnt)
        return node_id_pre_node_cnt

    def gen(self) -> None:
        """
        Generate execution chains from the workflow schema.
        This method processes the workflow graph and creates both master chains and iteration chains.
        """
        start_node_id, end_node_id, self.edge_dict, iteration_dict = self._deal_edges()

        next_root_results = self._get_next_node(start_node_id, self.edge_dict)

        # Process iteration node chains
        for iteration_node_id, iteration_node_id_start_id in iteration_dict.items():
            next_results = self._get_next_node(
                iteration_node_id_start_id, self.edge_dict
            )
            if iteration_node_id not in self.iteration_chains:
                self.iteration_chains[iteration_node_id] = Chains(
                    workflow_schema=self.workflow_schema
                )
            for next_result in next_results:
                node_id_list = [iteration_node_id_start_id, *next_result]
                sp = SimplePath(
                    node_id_list=node_id_list,
                    every_node_index=self._get_every_node_index(node_id_list),
                )
                self.iteration_chains[iteration_node_id].master_chains.append(sp)
                self.iteration_chains[iteration_node_id].edge_dict = self.edge_dict

        for next_root_result in next_root_results:
            node_id_list = [start_node_id, *next_root_result]
            sp = SimplePath(
                node_id_list=node_id_list,
                every_node_index=self._get_every_node_index(node_id_list),
            )
            self.master_chains.append(sp)
