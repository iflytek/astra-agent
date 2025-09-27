from typing import Any

from fastapi.exceptions import RequestValidationError
from pydantic import BaseModel, Field, model_validator

from api.schemas.llm_message import LLMMessage
from exceptions.agent_exc import AgentInternalExc


class MetaDataInputs(BaseModel):
    """extra inputs"""

    caller: str = Field(default="chat_open_api")
    caller_sid: str = Field(default="")


class BaseInputs(BaseModel):
    uid: str = Field(default="", description="uid", max_length=64)
    messages: list[LLMMessage]
    stream: bool = Field(default=False)
    meta_data: MetaDataInputs = Field(default_factory=MetaDataInputs)

    @model_validator(mode="before")
    @classmethod
    def validate_messages_params(cls, values: Any) -> Any:
        if not isinstance(values, dict):
            return values
        messages = values.get("messages", [])
        if isinstance(messages, list) and not messages:
            values.pop("messages", None)
            return values

        next_role = "user"
        for i, message in enumerate(messages):
            if not isinstance(message, dict):
                return values

            if not message.get("content"):
                # Content cannot be empty
                raise RequestValidationError(
                    errors=[
                        {
                            "type": "literal_error",
                            "loc": ("body", "messages", i, "content"),
                            "msg": "'content' cannot be empty",
                        }
                    ]
                )

            if message.get("role") == "system":
                # System role not supported
                raise RequestValidationError(
                    errors=[
                        {
                            "type": "literal_error",
                            "loc": ("body", "messages", i, "role"),
                            "msg": "'role' must be user or assistant",
                        }
                    ]
                )

            if message.get("role") != next_role:
                # Wrong order
                raise RequestValidationError(
                    errors=[
                        {
                            "type": "literal_error",
                            "loc": ("body", "messages", i, "role"),
                            "msg": (
                                "messages role order must alternate "
                                "between user and assistant"
                            ),
                        }
                    ]
                )

            next_role = "assistant" if next_role == "user" else "user"

        if next_role != "assistant":
            # Last message is not user
            raise RequestValidationError(
                errors=[
                    {
                        "type": "literal_error",
                        "loc": ("body", "messages"),
                        "msg": "messages must end with user type content",
                    }
                ]
            )

        return values

    def get_last_message_content(self) -> str:
        """
        Safely get the content of the last message.

        Returns:
            str: Content of the last message

        Raises:
            AgentInternalExc: If messages list is empty
        """
        if not self.messages:
            raise AgentInternalExc(
                "Messages list is empty, cannot get last message content"
            )
        return self.messages[-1].content

    def get_last_message_content_safe(self, default: str = "") -> str:
        """
        Safely get the content of the last message with a default value.

        Args:
            default: Default value to return if messages list is empty

        Returns:
            str: Content of the last message or default value
        """
        if not self.messages:
            return default
        return self.messages[-1].content

    def get_chat_history(self) -> list[LLMMessage]:
        """
        Safely get chat history (all messages except the last one).

        Returns:
            list[LLMMessage]: Chat history messages, empty list if no history
        """
        if len(self.messages) <= 1:
            return []
        return self.messages[:-1]

    def get_chat_history_safe(self) -> list[LLMMessage]:
        """
        Safely get chat history with additional safety checks.

        Returns:
            list[LLMMessage]: Chat history messages, always returns a list
        """
        return self.get_chat_history()
