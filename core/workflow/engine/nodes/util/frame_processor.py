import json
from abc import ABC, abstractmethod
from enum import Enum
from typing import Any, Dict, List, Type, Union, cast

from workflow.consts.engine.chat_status import ChatStatus, SparkLLMStatus
from workflow.consts.engine.model_provider import ModelProviderEnum
from workflow.consts.engine.tool_type import ToolType
from workflow.engine.entities.node_entities import NodeType
from workflow.exception.e import CustomException
from workflow.exception.errors.code_convert import CodeConvert


def generate_agent_output_optimize(
    type: str,
    reason: str,
    response: Union[str, List[Any]],
    function_name: str,
    function_arguments: str,
) -> str:
    """
    Generate optimized agent output in markdown format.

    This function formats agent tool execution results into a structured
    markdown output with JSON content for better readability.

    :param type: Type of the reasoning tool
    :param reason: Reasoning explanation for the tool usage
    :param response: Response data from the tool execution
    :param function_name: Name of the executed function
    :param function_arguments: Arguments passed to the function
    :return: Formatted markdown string with tool execution details
    """
    json_content = json.dumps(
        {"arguments": function_arguments, "name": function_name, "response": response},
        ensure_ascii=False,
        indent=4,
    )  # Ensure Chinese characters are not escaped and format is clear

    md = (
        f"**Reasoning tool {type}**\n"
        f"> {reason}\n\n"
        f"```json\n"
        f"{json_content}\n"
        f"```\n"
        "---"
    )
    return md


def extract_tool_calls_content(tool_calls: List[Dict[str, Any]]) -> str:
    """
    Extract and format content from tool calls.

    This function processes a list of tool calls, extracts relevant information
    from each tool call, and formats them into a readable markdown output.
    It handles different tool types including TOOL, KNOWLEDGE, and MCP tools.

    :param tool_calls: List of tool call dictionaries
    :return: Formatted string containing all tool call contents
    :raises CustomException: When tool execution returns error code
    """
    final_content = []
    for tool in tool_calls:
        type_value = str(tool.get("type") or "")
        reason_value = str(tool.get("reason") or "")
        function = cast(Dict[str, Any], tool.get("function") or {})
        response_json_str = function.get("response") or "{}"
        response_json = json.loads(response_json_str)
        if type_value == ToolType.TOOL.value:
            if response_json.get("header"):
                # Handle tool type response
                code = response_json.get("header", {}).get("code")
                if code != 0:
                    err_msg = response_json.get("header", {}).get("message", "")
                    raise CustomException(
                        err_code=CodeConvert.sparkLinkCode(code), err_msg=err_msg
                    )
                payload = response_json.get("payload", {})
                response = payload.get("text", {}).get("text", "")
                response_dict = json.loads(response) if response else {}
            else:
                # Handle MCP (Model Context Protocol) type response
                response_dict = response_json.get("data", {}).get("content", [])
        elif type_value == ToolType.KNOWLEDGE.value:
            response_dict = response_json.get("metadata_list", [])
        # response = function.get("response")
        function_name = str(function.get("name") or "")
        function_arguments = str(function.get("arguments") or "")
        final_content.append(
            generate_agent_output_optimize(
                type_value,
                reason_value,
                response_dict,
                function_name,
                function_arguments,
            )
        )
    return "\n".join(final_content)


class UnionFrame:
    """
    Unified frame structure for different LLM response formats.

    This class provides a standardized interface for handling responses
    from different LLM providers and node types.

    :param code: Response code indicating success or error status
    :param status: Status string or integer representing the current state
    :param text: Dictionary containing response content and metadata
    """

    def __init__(self, code: int, status: "str | int", text: dict):
        self.code = code
        self.status = status
        self.text = text

    def __repr__(self) -> str:
        return (
            f"UnionFrame(code={self.code}, status='{self.status}', text='{self.text}')"
        )


class FrameProcessor(ABC):
    """
    Abstract base class for processing LLM response frames.

    This class defines the interface for processing different types of
    LLM responses and converting them into a unified UnionFrame format.
    """

    @abstractmethod
    def process_frame(self, llm_response: Dict[str, Any]) -> UnionFrame:
        """
        Process LLM response and convert to unified frame format.

        :param llm_response: Raw LLM response dictionary
        :return: Unified frame object containing processed response data
        """
        pass


class AIPaaSFrameProcessor(FrameProcessor):
    """
    Frame processor for AI Platform as a Service (AIPaaS) responses.

    This processor handles responses from the AIPaaS platform,
    extracting code, status, and text content from the response structure.
    """

    def process_frame(self, llm_response: Dict[str, Any]) -> UnionFrame:
        """
        Process AIPaaS response frame.

        :param llm_response: AIPaaS response dictionary
        :return: Unified frame with extracted response data
        """
        code = llm_response["header"]["code"]
        status = llm_response["header"]["status"]
        resp_payload = llm_response["payload"]
        text = resp_payload["choices"]["text"][0]
        return UnionFrame(code, status, text)


class AgentFrameProcessor(FrameProcessor):
    """
    Frame processor for Agent node responses.

    This processor handles responses from agent nodes, including
    content, reasoning content, and tool calls processing.
    """

    def process_frame(self, llm_response: Dict[str, Any]) -> UnionFrame:
        """
        Process agent response frame.

        :param llm_response: Agent response dictionary
        :return: Unified frame with processed agent response data
        """
        code = llm_response.get("code", 0)
        status = SparkLLMStatus.RUNNING.value
        text = {"content": "", "reasoning_content": ""}
        is_finish: str = llm_response["choices"][0].get("finish_reason")
        if is_finish == ChatStatus.FINISH_REASON.value:
            status = SparkLLMStatus.END.value
        delta = llm_response["choices"][0].get("delta", {})
        if delta.get("content"):
            text["content"] = delta["content"]
        elif delta.get("reasoning_content"):
            text["reasoning_content"] = delta["reasoning_content"]
        elif delta.get("tool_calls"):
            # Process tool calls and format as reasoning content
            text["reasoning_content"] = extract_tool_calls_content(delta["tool_calls"])
        return UnionFrame(code, status, text)


class OpenAIFrameProcessor(FrameProcessor):
    """
    Frame processor for OpenAI API responses.

    This processor handles responses from OpenAI API endpoints,
    including content streaming and finish reason processing.
    """

    def process_frame(self, llm_response: Dict[str, Any]) -> UnionFrame:
        """
        Process OpenAI response frame.

        :param llm_response: OpenAI response dictionary
        :return: Unified frame with processed OpenAI response data
        """
        code = llm_response.get(
            "code", 0
        )  # Originally no code field in frame, but set to -1 when model node execution fails
        status = SparkLLMStatus.RUNNING.value
        text = {"content": "", "reasoning_content": ""}
        is_finish: str = llm_response["choices"][0].get("finish_reason")
        if is_finish == ChatStatus.FINISH_REASON.value:
            status = SparkLLMStatus.END.value
        if is_finish and is_finish != ChatStatus.FINISH_REASON.value:
            status = SparkLLMStatus.END.value
        delta = llm_response["choices"][0].get("delta", {})
        if delta.get("content"):
            text["content"] = delta["content"]
        return UnionFrame(code, status, text)


class KnowledgeProFrameProcessor(FrameProcessor):
    """
    Frame processor for Knowledge Pro node responses.

    This processor handles responses from knowledge base nodes,
    extracting content and processing finish reasons.
    """

    def process_frame(self, response: Dict[str, Any]) -> UnionFrame:
        """
        Process Knowledge Pro response frame.

        :param response: Knowledge Pro response dictionary
        :return: Unified frame with processed knowledge response data
        """
        code = response.get(
            "code", 0
        )  # Originally no code field in frame, but set to -1 when knowledge node execution fails
        status = SparkLLMStatus.RUNNING.value
        text = {
            "content": response.get("data", {}).get("content", ""),
            "reasoning_content": "",
        }
        is_finish = response.get("finish_reason", "")
        if is_finish == ChatStatus.FINISH_REASON.value:
            status = SparkLLMStatus.END.value
        return UnionFrame(code, status, text)


class FlowFrameProcessor(FrameProcessor):
    """
    Frame processor for Flow node responses.

    This processor handles responses from flow nodes,
    processing content and reasoning content from the response.
    """

    def process_frame(self, response: Dict[str, Any]) -> UnionFrame:
        """
        Process Flow response frame.

        :param response: Flow response dictionary
        :return: Unified frame with processed flow response data
        """
        code = response.get("code", 0)
        status = SparkLLMStatus.RUNNING.value
        text = {"content": "", "reasoning_content": ""}
        if len(response["choices"]) > 0:
            delta = response["choices"][0].get("delta", {})
            if delta.get("content"):
                text["content"] = delta["content"]
            if delta.get("reasoning_content"):
                text["reasoning_content"] = delta["reasoning_content"]
            is_finish = response["choices"][0].get("finish_reason")
            if is_finish == ChatStatus.FINISH_REASON.value:
                status = SparkLLMStatus.END.value
        return UnionFrame(code, status, text)


class FrameProcessorEnum(Enum):
    """
    Enumeration of supported frame processor types.

    This enum defines the different types of frame processors
    available for handling various LLM response formats.
    """

    XINGHUO = ModelProviderEnum.XINGHUO.value
    OPENAI = ModelProviderEnum.OPENAI.value
    AGENT = NodeType.AGENT.value
    KNOWLEDGE_PRO = NodeType.KNOWLEDGE_PRO.value
    FLOW = NodeType.FLOW.value


class FrameProcessorFactory:
    """
    Factory class for creating frame processors based on protocol type.

    This factory provides a centralized way to create appropriate
    frame processors for different LLM response protocols.
    """

    _processors: Dict[str, Type[FrameProcessor]] = {
        FrameProcessorEnum.XINGHUO.value: AIPaaSFrameProcessor,
        FrameProcessorEnum.AGENT.value: AgentFrameProcessor,
        FrameProcessorEnum.OPENAI.value: OpenAIFrameProcessor,
        FrameProcessorEnum.KNOWLEDGE_PRO.value: KnowledgeProFrameProcessor,
        FrameProcessorEnum.FLOW.value: FlowFrameProcessor,
    }

    @staticmethod
    def get_processor(protocol: str) -> FrameProcessor:
        """
        Get frame processor instance for the specified protocol.

        :param protocol: Protocol type string
        :return: Frame processor instance for the protocol
        :raises ValueError: If protocol is not supported
        """
        processor_class = FrameProcessorFactory._processors.get(protocol)
        if not processor_class:
            raise ValueError(f"Unsupported protocol: {protocol}")
        # All registered processors are concrete subclasses of FrameProcessor
        return cast(FrameProcessor, processor_class())
