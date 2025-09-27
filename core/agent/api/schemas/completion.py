from typing import List, Literal, Optional

from openai.types.chat.chat_completion import ChatCompletion, Choice
from openai.types.chat.chat_completion_message import ChatCompletionMessage
from openai.types.chat.chat_completion_message_tool_call import (
    ChatCompletionMessageToolCall,
    Function,
)
from pydantic import Field


class ReasonChoiceMessageToolCallFunction(Function):
    arguments: Optional[str] = None  # type: ignore[assignment]
    name: Optional[str] = None  # type: ignore[assignment]
    response: Optional[str] = None


class ReasonChoiceMessageToolCall(ChatCompletionMessageToolCall):
    id: str = Field(default="")
    reason: str = Field(default="")
    function: Optional[ReasonChoiceMessageToolCallFunction] = None  # type: ignore
    type: Optional[Literal["workflow", "tool", "knowledge"]] = None  # type: ignore


class ReasonChoiceMessage(ChatCompletionMessage):
    reasoning_content: Optional[str] = Field(default="")
    content: Optional[str] = Field(default="")
    tool_calls: Optional[List[ReasonChoiceMessageToolCall]] = Field(  # type: ignore
        default_factory=list
    )
    role: Optional[Literal["assistant"]] = Field(default="assistant")  # type: ignore


class ReasonChoice(Choice):
    index: Optional[int] = Field(default=0)  # type: ignore[assignment]
    message: ReasonChoiceMessage  # pyright: ignore[reportIncompatibleVariableOverride]
    finish_reason: Optional[
        Literal["stop", "length", "tool_calls", "content_filter", "function_call"]
    ] = None  # type: ignore[assignment]


class ReasonChatCompletion(ChatCompletion):
    choices: List[ReasonChoice] = Field(default_factory=list)  # type: ignore
    code: int = Field(default=0)
    message: str = Field(default="success")
    logs: list[str] = Field(default_factory=list)
    knowledge_metadata: list[str] = Field(default_factory=list)
