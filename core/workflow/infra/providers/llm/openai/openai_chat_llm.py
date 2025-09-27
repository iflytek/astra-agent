"""
OpenAI Chat AI implementation for LLM interactions.

This module provides an asynchronous interface for communicating with OpenAI's
chat completion API, including streaming support and error handling.
"""

import asyncio
import json
from typing import Any, AsyncIterator, Dict, Tuple

from openai import AsyncOpenAI  # type: ignore
from workflow.consts.engine.chat_status import ChatStatus
from workflow.engine.nodes.entities.llm_response import LLMResponse
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.providers.llm.chat_ai import ChatAI


class OpenAIChatAI(ChatAI):
    """
    OpenAI Chat AI implementation for handling chat completions.

    This class extends the base ChatAI class to provide OpenAI-specific
    functionality including streaming responses, token calculation, and
    message processing.
    """

    model_config = {"arbitrary_types_allowed": True, "protected_namespaces": ()}

    def token_calculation(self, text: str) -> int:
        """
        Calculate the number of tokens in the given text.

        :param text: Input text to calculate tokens for
        :return: Number of tokens in the text
        """
        raise NotImplementedError

    def image_processing(self, image_path: str) -> Any:
        """
        Process an image for LLM input.

        :param image_path: Path to the image file
        :return: Processed image data
        """
        raise NotImplementedError

    def assemble_url(self, span: Span) -> str:
        """
        Assemble and validate the OpenAI API URL.

        :param span: Tracing span for logging
        :return: Validated API URL
        :raises CustomException: If the URL is empty or invalid
        """
        self.model_url = self.model_url.rsplit("/", 2)[0]
        if not self.model_url:
            raise CustomException(
                err_code=CodeEnum.OPEN_AI_REQUEST_ERROR,
                err_msg="Request URL is empty",
                cause_error="Request URL is empty",
            )
        span.add_info_events({"openai_base_url": self.model_url})
        return self.model_url

    def assemble_payload(self, message: list) -> str:
        """
        Assemble the request payload data.

        :param message: List of messages to include in the payload
        :return: Serialized payload string
        """
        raise NotImplementedError

    def decode_message(self, msg: dict) -> Tuple[int, str, str, str, Dict[str, Any]]:
        """
        Decode a message from OpenAI API response.

        :param msg: Raw message dictionary from OpenAI API
        :return: Tuple containing (index, status, content, reasoning_content, token_usage)
        """
        delta = msg["choices"][0]["delta"]
        status = msg["choices"][0]["finish_reason"]
        content = delta["content"]
        reasoning_content = delta.get("reasoning_content", "")
        token_usage = {} if not msg["usage"] else msg["usage"]
        return 0, status, content, reasoning_content, token_usage

    async def _recv_messages(
        self,
        url: str,
        user_message: list,
        extra_params: dict,
        span: Span,
        timeout: float | None = None,
    ) -> AsyncIterator[LLMResponse]:
        """
        Receive streaming messages from OpenAI API.

        :param url: OpenAI API base URL
        :param user_message: List of messages to send
        :param extra_params: Additional parameters for the API request
        :param span: Tracing span for logging
        :param timeout: Optional timeout for frame processing
        :return: Async iterator of LLMResponse objects
        :raises CustomException: If request times out or fails
        """
        # Initialize OpenAI async client
        aclient = AsyncOpenAI(
            api_key=self.api_key,
            base_url=url,
        )
        # Create streaming chat completion
        stream = await aclient.chat.completions.create(
            model=self.model_name,
            messages=user_message,
            stream=True,
            **extra_params,
        )
        # Initialize tracking variables for streaming
        last_frame_data = {}
        is_first_frame = True
        start_time = None
        while True:
            try:
                if timeout is not None:
                    if is_first_frame:
                        start_time = asyncio.get_event_loop().time()
                    # Frame timeout control
                    chunk = await asyncio.wait_for(stream.__anext__(), timeout=timeout)
                else:
                    chunk = await stream.__anext__()
                # Track first frame timing for performance monitoring
                if is_first_frame:
                    is_first_frame = False
                    if start_time is not None:
                        first_frame_cost = asyncio.get_event_loop().time() - start_time
                        span.add_info_events({"llm first token cost": first_frame_cost})

                # Log received chunk data
                span.add_info_events(
                    {"recv": json.dumps(chunk.dict(), ensure_ascii=False)}
                )

                # Update last frame data and yield response
                last_frame_data = chunk.dict()
                yield LLMResponse(
                    msg=last_frame_data,
                )
            except StopAsyncIteration:
                # Stream ended, mark as finished and yield final response
                last_frame_data["choices"][0][
                    "finish_reason"
                ] = ChatStatus.FINISH_REASON.value
                yield LLMResponse(
                    msg=last_frame_data,
                )
                break
            except asyncio.TimeoutError as e:
                # Handle timeout error
                raise CustomException(
                    err_code=CodeEnum.OPEN_AI_REQUEST_ERROR,
                    err_msg=f"LLM response timeout ({timeout}s)",
                    cause_error=f"LLM response timeout ({timeout}s)",
                ) from e

    async def achat(
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
        Send chat request and handle streaming response.

        :param flow_id: Unique identifier for the workflow flow
        :param user_message: List of messages to send to the LLM
        :param span: Tracing span for logging and monitoring
        :param extra_params: Additional parameters for the API request
        :param timeout: Optional timeout for the request
        :param search_disable: Whether to disable search functionality
        :param event_log_node_trace: Optional node trace logger
        :return: Async iterator of LLMResponse objects
        :raises CustomException: If request fails or times out
        """
        # Assemble API URL and log request information
        url = self.assemble_url(span)
        span.add_info_events({"domain": self.model_name})
        span.add_info_events(
            {"extra_params": json.dumps(extra_params, ensure_ascii=False)}
        )

        try:

            # Log configuration data if trace logger is provided
            if event_log_node_trace:
                event_log_node_trace.append_config_data(
                    {
                        "model_name": self.model_name,
                        "base_url": url,
                        "message": user_message,
                        "extra_params": extra_params,
                    }
                )

            # Process streaming messages and yield responses
            async for msg in self._recv_messages(
                url, user_message, extra_params, span, timeout
            ):
                # Log message data if trace logger is provided
                if event_log_node_trace:
                    event_log_node_trace.add_info_log(
                        json.dumps(msg.msg, ensure_ascii=False)
                    )
                yield msg
        except CustomException as e:
            # Re-raise custom exceptions as-is
            raise e
        except Exception as e:
            # Record exception in span and wrap in custom exception
            span.record_exception(e)
            raise CustomException(
                err_code=CodeEnum.OPEN_AI_REQUEST_ERROR,
                err_msg=str(e),
                cause_error=str(e),
            )
