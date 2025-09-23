import asyncio
import json
import os
from typing import Any, Dict, List, Tuple

import aiohttp
from aiohttp import ClientResponse, ClientTimeout
from pydantic import BaseModel

from workflow.consts.flow import FLOW_FINISH_REASON
from workflow.consts.model_provider import ModelProviderEnum
from workflow.engine.callbacks.openai_types_sse import GenerateUsage
from workflow.engine.entities.msg_or_end_dep_info import MsgOrEndDepInfo
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.knowledge_pro.consts import RepoTypeEnum
from workflow.engine.nodes.util.dict_util import keys_to_snake_case
from workflow.engine.nodes.util.frame_processor import extract_tool_calls_content
from workflow.engine.nodes.util.prompt import prompt_template_replace
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.providers.llm.iflytek_spark.schemas import StreamOutputMsg


# Temporarily unused
class Instruction(BaseModel):
    """Instruction configuration for agent node.

    :param reasoning: Reasoning instruction template
    :param answer: Answer instruction template
    :param query: Query instruction template
    """

    reasoning: str = ""
    answer: str = ""
    query: str = ""


class AgentModelConfig(BaseModel):
    """Agent model configuration.

    :param domain: Model domain identifier
    :param api: API endpoint URL
    :param agentStrategy: Agent strategy type (1 for default)
    """

    domain: str = ""
    api: str = ""
    agentStrategy: int = 1


class Match(BaseModel):
    """Knowledge base matching configuration.

    :param repoIds: List of repository IDs to match
    :param docIds: List of document IDs to match
    """

    repoIds: list = []
    docIds: list = []


class Knowledge(BaseModel):
    """Knowledge base configuration for agent.

    :param name: Knowledge base name
    :param description: Knowledge base description
    :param topK: Number of top results to retrieve
    :param repoType: Repository type (default: CBG_RAG)
    :param match: Matching configuration for repositories and documents
    """

    name: str = ""
    description: str = ""
    topK: int = 1
    repoType: int = RepoTypeEnum.CBG_RAG.value
    match: Match = Match()


class AgentNodePlugin(BaseModel):
    """Plugin configuration for agent node.

    :param mcpServerIds: List of MCP server IDs
    :param mcpServerUrls: List of MCP server URLs
    :param tools: List of available tools
    :param workflowIds: List of workflow IDs
    :param knowledge: List of knowledge base configurations
    """

    mcpServerIds: list = []
    mcpServerUrls: list = []
    tools: list = []
    workflowIds: list = []
    knowledge: List[Knowledge] = []


class AgentNodeMessage:
    """Message structure for agent communication.

    :param role: Message role (user, assistant, system)
    :param content: Message content
    """

    role: str
    content: str

    def __init__(self, role: str, content: str):
        """Initialize agent message.

        :param role: Message role
        :param content: Message content
        """
        self.role = role
        self.content = content

    def to_dict(self) -> dict:
        """Convert message to dictionary format.

        :return: Dictionary representation of the message
        """
        return {"role": self.role, "content": self.content}


class AgentMetaData(BaseModel):
    """Metadata configuration for agent node.

    :param caller: Caller identifier (default: workflow-agent-node)
    :param callerSid: Caller session ID
    """

    caller: str = "workflow-agent-node"
    callerSid: str = ""


class AgentNode(BaseNode):
    """Agent node implementation for workflow execution.

    This class handles agent-based workflow nodes that can interact with
    external AI services, use tools, and access knowledge bases.

    :param appId: Application ID for authentication
    :param apiKey: API key for authentication
    :param apiSecret: API secret for authentication
    :param uid: User identifier
    :param modelConfig: Model configuration settings
    :param instruction: Instruction templates for reasoning, answer, and query
    :param plugin: Plugin configuration including tools and knowledge bases
    :param metaData: Metadata configuration
    :param maxLoopCount: Maximum number of execution loops (default: 10)
    :param stream: Whether to use streaming mode (default: True)
    :param maxTokens: Maximum token limit (default: 10240)
    :param enableChatHistoryV2: Chat history configuration
    :param source: Model provider source (default: XINGHUO)
    """

    appId: str
    apiKey: str
    apiSecret: str
    uid: str
    modelConfig: AgentModelConfig
    instruction: Instruction
    plugin: AgentNodePlugin
    metaData: AgentMetaData = AgentMetaData()
    maxLoopCount: int = 10
    stream: bool = True
    maxTokens: int = 10240
    enableChatHistoryV2: dict = {}
    source: str = ModelProviderEnum.XINGHUO.value

    def get_node_config(self) -> Dict[str, Any]:
        """Get node configuration as dictionary.

        :return: Dictionary containing all node configuration parameters
        """
        return {
            "app_id": self.appId,
            "api_key": self.apiKey,
            "api_secret": self.apiSecret,
            "uid": self.uid,
            "modelConfig": self.modelConfig.dict(),
            "instruction": self.instruction.dict(),
            "plugin": self.plugin.dict(),
            "metaData": self.metaData.dict(),
            "max_loop_count": self.maxLoopCount,
            "maxTokens": self.maxTokens,
            "enableChatHistoryV2": self.enableChatHistoryV2,
            "knowledge": [k.dict() for k in self.plugin.knowledge],
            "source": self.source,
        }

    def sync_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """Synchronous execution method (not implemented for agent nodes).

        :param variable_pool: Variable pool for data storage
        :param span: Tracing span for monitoring
        :param event_log_node_trace: Event logging for node execution
        :param kwargs: Additional keyword arguments
        :return: Node execution result
        :raises NotImplementedError: Always raised as agent nodes use async execution
        """
        raise NotImplementedError

    async def _call_agent(
        self,
        inputs: dict,
        variable_pool: VariablePool,
        msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo],
        span: Span,
        event_log_node_trace: NodeLog | None = None,
    ) -> Tuple[list, list, dict]:
        """Call external agent service with prepared inputs.

        :param inputs: Input data for the agent
        :param variable_pool: Variable pool for data storage
        :param msg_or_end_node_deps: Message or end node dependencies
        :param span: Tracing span for monitoring
        :param event_log_node_trace: Event logging for node execution
        :return: Tuple of (content_list, reasoning_content_list, token_usage)
        """
        # Prepare instruction templates
        reasoning_instruction, answer_instruction, query_instruction = (
            self._prepare_instructions(variable_pool, span)
        )

        messages = self._deal_history(inputs, variable_pool, span)
        messages.append(AgentNodeMessage("user", query_instruction).to_dict())
        span.add_info_event(f"messages: {messages}")

        self._normalize_tools()

        # Construct request headers
        headers = {
            "Content-Type": "application/json",
            "x-consumer-username": self.appId,
        }

        req_body = self._generate_agent_request(
            reasoning_instruction, answer_instruction, messages, span
        )
        span.add_info_event(f"req header: {headers}")
        span.add_info_event(f"req body: {req_body}")

        if event_log_node_trace:
            event_log_node_trace.append_config_data(
                {
                    "url": os.getenv("AGENT_API_URL"),
                    "req_headers": headers,
                    "req_body": json.dumps(req_body, ensure_ascii=False),
                }
            )

        try:
            interval_timeout = (
                self.retry_config.timeout if self.retry_config.should_retry else None
            )

            timeout_config = ClientTimeout(
                total=30 * 60, sock_connect=30, sock_read=interval_timeout
            )

            async with aiohttp.ClientSession(timeout=timeout_config) as session:
                async with session.post(
                    url=os.getenv("AGENT_API_URL", ""), headers=headers, json=req_body
                ) as response:
                    content_list, reasoning_content_list, token_usage = (
                        await self._process_stream_response(
                            response, variable_pool, msg_or_end_node_deps, span
                        )
                    )
        except asyncio.TimeoutError as e:
            raise CustomException(
                err_code=CodeEnum.AgentNodeExecutionError,
                err_msg=f"Agent node response timeout ({interval_timeout}s)",
            ) from e

        except CustomException as err:
            raise err
        except Exception as e:
            raise e

        return content_list, reasoning_content_list, token_usage

    def _prepare_instructions(
        self, variable_pool: VariablePool, span: Span
    ) -> Tuple[str, str, str]:
        """Prepare instruction templates by replacing variables.

        :param variable_pool: Variable pool containing template variables
        :param span: Tracing span for monitoring
        :return: Tuple of (reasoning_instruction, answer_instruction, query_instruction)
        """
        reasoning_instruction = prompt_template_replace(
            input_identifier=self.input_identifier,
            _prompt_template=self.instruction.reasoning,
            node_id=self.node_id,
            variable_pool=variable_pool,
            span_context=span,
        )

        answer_instruction = prompt_template_replace(
            input_identifier=self.input_identifier,
            _prompt_template=self.instruction.answer,
            node_id=self.node_id,
            variable_pool=variable_pool,
            span_context=span,
        )

        query_instruction = prompt_template_replace(
            input_identifier=self.input_identifier,
            _prompt_template=self.instruction.query,
            node_id=self.node_id,
            variable_pool=variable_pool,
            span_context=span,
        )

        return (
            reasoning_instruction,
            answer_instruction,
            query_instruction,
        )

    async def _process_stream_response(
        self,
        response: ClientResponse,
        variable_pool: VariablePool,
        msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo],
        span: Span,
    ) -> Tuple[list, list, dict]:
        """Process streaming response from agent service.

        :param response: HTTP response from agent service
        :param variable_pool: Variable pool for data storage
        :param msg_or_end_node_deps: Message or end node dependencies
        :param span: Tracing span for monitoring
        :return: Tuple of (content_list, reasoning_content_list, token_usage)
        """

        content_list = []
        reasoning_content_list = []
        token_usage = {}
        async for line in response.content:
            line_str = line.decode("utf-8")
            if line_str == "\n":
                continue

            span.add_info_event(f"recv: {line_str}")
            msg = json.loads(line_str.removeprefix("data:"))
            if line_str == "[DONE]":
                break

            if msg.get("code", 0) != 0:
                raise CustomException(
                    err_code=CodeEnum.AgentNodeExecutionError,
                    err_msg=msg.get("message", ""),
                    cause_error=json.dumps(msg, ensure_ascii=False),
                )

            choices = msg.get("choices", [{}])
            if len(choices) == 0:
                break

            content = choices[0].get("delta", {}).get("content", "")
            reasoning_content = choices[0].get("delta", {}).get("reasoning_content", "")
            tool_calls = choices[0].get("delta", {}).get("tool_calls", [])
            if content:
                content_list.append(content)
            if reasoning_content:
                reasoning_content_list.append(reasoning_content)
            if tool_calls:
                tool_calls_optimize = extract_tool_calls_content(tool_calls)
                reasoning_content_list.append(tool_calls_optimize)

            finish_reason = choices[0].get("finish_reason", FLOW_FINISH_REASON)
            # Put frame content into msg_or_end_node_deps for streaming
            # await self.put_agent_content(self.node_id, variable_pool, msg_or_end_node_deps, msg)
            await self.put_stream_content(
                self.node_id,
                variable_pool,
                msg_or_end_node_deps,
                self.modelConfig.domain,
                msg,
            )
            if finish_reason == FLOW_FINISH_REASON:
                token_usage = msg.get("usage", {})
                token_usage = token_usage if token_usage else {}
                break
        return content_list, reasoning_content_list, token_usage

    def _generate_agent_request(
        self,
        reasoning_instruction: str,
        answer_instruction: str,
        messages: List[Dict],
        span: Span,
    ) -> dict:
        """Generate request body for agent service call.

        :param reasoning_instruction: Processed reasoning instruction
        :param answer_instruction: Processed answer instruction
        :param messages: List of conversation messages
        :param span: Tracing span for monitoring
        :return: Request body dictionary
        """
        return {
            "model_config": {
                "domain": self.modelConfig.domain,
                "api": (
                    self.modelConfig.api.rsplit("/", 2)[0]
                    if self.source == ModelProviderEnum.OPENAI.value
                    else self.modelConfig.api
                ),
                "api_key": (
                    f"{self.apiKey}:{self.apiSecret}"
                    if self.source == ModelProviderEnum.XINGHUO.value
                    else self.apiKey
                ),
            },
            "instruction": {
                "reasoning": reasoning_instruction,
                "answer": answer_instruction,
            },
            "plugin": {
                "tools": self.plugin.tools,
                "mcp_server_ids": self.plugin.mcpServerIds,
                "mcp_server_urls": self.plugin.mcpServerUrls,
                "workflow_ids": self.plugin.workflowIds,
                "knowledge": keys_to_snake_case(
                    [k.dict() for k in self.plugin.knowledge]
                ),
            },
            "uid": span.uid,
            "messages": messages,
            "meta_data": {
                "caller": self.metaData.caller,
                "caller_sid": self.metaData.callerSid,
            },
            "stream": True,
            "max_loop_count": self.maxLoopCount,
        }

    def _normalize_tools(self) -> None:
        """Normalize tool configuration format.

        Converts string tool IDs to proper tool configuration objects.
        """
        for index, tool in enumerate(self.plugin.tools):
            if isinstance(tool, str):
                self.plugin.tools[index] = {"tool_id": tool, "version": "V1.0"}

    def _deal_history(
        self,
        inputs: Dict,
        variable_pool: VariablePool,
        span: Span,
    ) -> List[Dict]:
        """Process chat history for agent context.

        :param inputs: Input data dictionary
        :param variable_pool: Variable pool containing history data
        :param span: Tracing span for monitoring
        :return: List of formatted message dictionaries
        """
        messages: List[Dict] = []
        if not (self.enableChatHistoryV2 and self.enableChatHistoryV2.get("isEnabled")):
            return messages
        rounds = self.enableChatHistoryV2.get("rounds", 10)
        history = []
        # variable_pool.history_v2 is None during single node debugging
        if variable_pool.history_v2:
            history = variable_pool.history_v2.process_history(
                data=variable_pool.history_v2.origin_history,
                max_token=self.maxTokens,
                rounds=rounds,
            )
        for item in history:
            # Multimodal content is not currently supported
            if item.content_type == "text":
                messages.append(
                    AgentNodeMessage(role=item.role, content=item.content).to_dict()
                )
        inputs.update({"chatHistory": messages})
        span.add_info_event(f"history: {history}")
        return messages

    async def put_agent_content(
        self,
        node_id: str,
        variable_pool: VariablePool,
        msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo],
        agent_content: dict,
    ) -> None:
        """Put agent response content into streaming queue.

        :param node_id: Node identifier
        :param variable_pool: Variable pool for data storage
        :param msg_or_end_node_deps: Message or end node dependencies
        :param agent_content: Agent response content to queue
        """
        try:
            if not variable_pool.get_stream_node_has_sent_first_token(node_id):
                # Once put_llm_content is executed, it proves the agent has sent the first frame
                # Set has_sent_first_token to True
                variable_pool.set_stream_node_has_sent_first_token(node_id)
            if not msg_or_end_node_deps:
                # No node dependencies during single node debugging
                return

            if not variable_pool.stream_data:
                return

            for msg_end_node, info in msg_or_end_node_deps.items():
                data_dep = info.data_dep
                if node_id in data_dep:
                    await variable_pool.stream_data[msg_end_node][node_id].put(
                        StreamOutputMsg(
                            domain=self.modelConfig.domain, llm_response=agent_content
                        )
                    )
        except Exception as e:
            raise e

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """Execute agent node asynchronously.

        :param variable_pool: Variable pool for data storage
        :param span: Tracing span for monitoring
        :param event_log_node_trace: Event logging for node execution
        :param kwargs: Additional keyword arguments
        :return: Node execution result
        """
        try:
            self.metaData.callerSid = span.sid
            msg_or_end_node_deps = kwargs.get("msg_or_end_node_deps", {})
            inputs = {}
            for input_key in self.input_identifier:
                input_value = variable_pool.get_variable(
                    node_id=self.node_id, key_name=input_key, span=span
                )
                inputs.update({input_key: input_value})

            (content_list, reasoning_content_list, token_usage) = (
                await self._call_agent(
                    inputs,
                    variable_pool,
                    msg_or_end_node_deps,
                    span,
                    event_log_node_trace=event_log_node_trace,
                )
            )

            outputs = {}
            for output_key in self.output_identifier:
                if output_key == "REASONING_CONTENT":
                    outputs.update({output_key: "".join(reasoning_content_list)})
                else:
                    outputs.update({output_key: "".join(content_list)})

            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                process_data={},
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=inputs,
                outputs=outputs,
                token_cost=GenerateUsage(
                    completion_tokens=token_usage.get("completion_tokens", 0),
                    prompt_tokens=token_usage.get("prompt_tokens", 0),
                    total_tokens=token_usage.get("total_tokens", 0),
                ),
            )
        except CustomException as err:
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=err,
            )
        except Exception as err:
            span.record_exception(err)
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=CustomException(
                    CodeEnum.AgentNodeExecutionError,
                    cause_error=err,
                ),
            )
