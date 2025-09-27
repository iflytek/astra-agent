import abc
from asyncio import Event
from typing import Any, AsyncIterator

from pydantic import BaseModel, Field

from workflow.engine.nodes.entities.llm_response import LLMResponse
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span


class ChatAI(abc.ABC, BaseModel):
    """
    Abstract base class for chat AI providers.

    This class defines the interface for different LLM (Large Language Model) providers,
    including methods for token calculation, image processing, URL assembly, payload
    construction, message decoding, and asynchronous chat functionality.
    """

    model_url: str  # URL endpoint for the LLM model API
    model_name: str  # Name identifier of the LLM model
    temperature: float  # Sampling temperature for response generation (0.0 to 1.0)
    app_id: str  # Application identifier for API authentication
    api_key: str  # API key for authentication
    api_secret: str  # API secret for authentication
    max_tokens: int  # Maximum number of tokens to generate in response
    top_k: int  # Number of top-k tokens to consider during sampling
    uid: str  # User identifier
    stream_node_first_token: Event = Field(
        default_factory=Event
    )  # Event flag indicating whether the first token of streaming node has been sent

    model_config = {"arbitrary_types_allowed": True, "protected_namespaces": ()}

    @abc.abstractmethod
    def token_calculation(self, text: str) -> int:
        """
        Calculate the number of tokens in the given text.

        :param text: Input text to calculate tokens for
        :return: Number of tokens in the text
        """
        pass

    @abc.abstractmethod
    def image_processing(self, image_path: str) -> Any:
        """
        Process and prepare image data for LLM input.

        :param image_path: Path to the image file to be processed
        :return: Processed image data ready for LLM consumption
        """
        pass

    @abc.abstractmethod
    def assemble_url(self, span: Span) -> Any:
        """
        Assemble and construct the complete URL with authentication for API requests.

        :param span: Tracing span for observability and monitoring
        :return: Complete URL with authentication parameters appended
        """
        pass

    @abc.abstractmethod
    def assemble_payload(self, message: list) -> Any:
        """
        Assemble and construct the request payload data for API calls.

        :param message: List of message objects to be included in the payload
        :return: Serialized payload string ready for API transmission
        """
        pass

    @abc.abstractmethod
    def decode_message(self, msg: Any) -> Any:
        """
        Decode and parse the response message data from the LLM API.

        :param msg: Raw message data received from the API response
        :return: Parsed and decoded message content
        """
        pass

    @abc.abstractmethod
    def achat(
        self,
        flow_id: str,
        user_message: list,
        span: Span,
        extra_params: dict = {},
        timeout: float | None = None,
        search_disable: bool = True,
        event_log_node_trace: NodeLog | None = None,
    ) -> AsyncIterator[LLMResponse]:
        """
        Send asynchronous chat request to LLM and process streaming response.

        :param flow_id: Unique identifier for the workflow flow
        :param user_message: List of user messages to send to the LLM
        :param span: Tracing span for observability and monitoring
        :param extra_params: Additional parameters for the API request
        :param timeout: Request timeout in seconds (None for no timeout)
        :param search_disable: Flag to disable search functionality
        :param event_log_node_trace: Node logging trace for debugging and monitoring
        :return: AsyncIterator yielding LLMResponse objects from streaming response
        """
        pass
