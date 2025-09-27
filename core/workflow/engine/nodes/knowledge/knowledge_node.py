import json
import os
from typing import Any

from pydantic import Field
from workflow.engine.entities.history import EnableChatHistoryV2, History
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.knowledge.knowledge_client import (
    KnowledgeClient,
    KnowledgeConfig,
)
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span


class KnowledgeNode(BaseNode):
    """
    Knowledge base node for retrieving relevant information from knowledge repositories.

    This node performs semantic search against configured knowledge repositories
    and returns the most relevant results based on the input query.
    """

    topN: str = Field(default="5", min_length=1)  # Number of top results to retrieve
    ragType: str = Field(
        default="AIUI-RAG2"
    )  # Type of RAG (Retrieval-Augmented Generation) to use
    repoId: list[str] = Field(...)  # List of repository IDs to search in
    docIds: list = Field(
        default_factory=list
    )  # Optional list of specific document IDs to search
    flowId: str = Field(default="")  # Optional flow ID for context
    score: float = Field(default=0.1)  # Minimum similarity threshold for results
    enableChatHistoryV2: EnableChatHistoryV2 = Field(
        default_factory=EnableChatHistoryV2
    )

    @property
    def run_s(self) -> WorkflowNodeExecutionStatus:
        """
        Get the success execution status.

        :return: SUCCEEDED status for successful execution
        """
        return WorkflowNodeExecutionStatus.SUCCEEDED

    @property
    def run_f(self) -> WorkflowNodeExecutionStatus:
        """
        Get the failure execution status.

        :return: FAILED status for failed execution
        """
        return WorkflowNodeExecutionStatus.FAILED

    async def execute(
        self, variable_pool: VariablePool, span: Span, **kwargs: Any
    ) -> NodeRunResult:
        """
        Execute the knowledge base search operation.

        Retrieves the query from the variable pool, performs a knowledge base search,
        and returns the results in a NodeRunResult object.

        :param variable_pool: Pool containing workflow variables
        :param span: Span object for tracing and logging
        :param kwargs: Additional keyword arguments including event_log_node_trace
        :return: NodeRunResult containing the search results or error information
        """
        try:
            event_log_node_trace = kwargs.get("event_log_node_trace")
            # Get the query from the variable pool
            query = variable_pool.get_variable(
                node_id=self.node_id, key_name=self.input_identifier[0], span=span
            )
            inputs, outputs = {self.input_identifier[0]: query}, {}
            if not isinstance(query, str):
                query = str(query)
            status = self.run_s
            # Process chat history if enabled
            history = []
            if self.enableChatHistoryV2.is_enabled:
                rounds = self.enableChatHistoryV2.rounds
                if variable_pool.history_v2:
                    history_v2 = History(
                        origin_history=variable_pool.history_v2.origin_history,
                        rounds=rounds,
                    )
                    history = history_v2.origin_history

            # Get knowledge base URL from environment variables
            knowledge_recall_url = os.getenv("KNOWLEDGE_URL", "")
            knowledge_config = KnowledgeConfig(
                top_n=self.topN,
                rag_type=self.ragType,
                repo_id=self.repoId,
                url=knowledge_recall_url,
                query=str(query),
                flow_id=self.flowId,
                doc_ids=self.docIds,
                threshold=self.score,
                history=history,
            )
            # Perform knowledge base search
            search_result = await KnowledgeClient(config=knowledge_config).top_k(
                request_span=span, event_log_node_trace=event_log_node_trace
            )
            result_dict = json.loads(search_result)["results"]
            outputs = {self.output_identifier[0]: result_dict}

            return NodeRunResult(
                status=status,
                inputs=inputs,
                outputs=outputs,
                raw_output=str(search_result),
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )
        except CustomException as err:
            status = self.run_f
            span.add_error_event(str(err))
            span.record_exception(err)
            return NodeRunResult(
                status=status,
                error=err,
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )
        except Exception as e:
            span.record_exception(e)
            status = self.run_f
            return NodeRunResult(
                status=status,
                error=CustomException(
                    CodeEnum.KNOWLEDGE_NODE_EXECUTION_ERROR,
                    cause_error=e,
                ),
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronous execution method.

        Delegates to the main execute method for asynchronous knowledge base search.

        :param variable_pool: Pool containing workflow variables
        :param span: Span object for tracing and logging
        :param event_log_node_trace: Optional node log trace object
        :param kwargs: Additional keyword arguments
        :return: NodeRunResult containing the search results or error information
        """
        return await self.execute(
            variable_pool, span, event_log_node_trace=event_log_node_trace, **kwargs
        )
