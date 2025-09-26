"""
Main implementation of iFlytek Spark Chat LLM provider.

This module provides the core functionality for interacting with iFlytek's Spark Chat API,
including WebSocket connection management, message handling, and streaming responses.
"""

import asyncio
import json
import os
import time
from typing import Any, AsyncIterator, Dict, Tuple

import websockets
from tenacity import retry, retry_if_exception_type, stop_after_attempt

from workflow.consts.engine.chat_status import SparkLLMStatus
from workflow.engine.nodes.entities.llm_response import LLMResponse
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.providers.llm.chat_ai import ChatAI
from workflow.infra.providers.llm.iflytek_spark.const import RETRY_CNT, spark_mapping
from workflow.infra.providers.llm.iflytek_spark.spark_chat_auth import SparkChatHmacAuth


@retry(
    stop=stop_after_attempt(RETRY_CNT),  # Maximum retry attempts
    retry=retry_if_exception_type(
        websockets.ConnectionClosed
    ),  # Retry only on connection closed
)
async def recv_with_retry(ws_handle: websockets.WebSocketClientProtocol) -> str | bytes:
    """
    Receive message from WebSocket with retry mechanism.

    :param ws_handle: WebSocket client protocol handle
    :return: Received message data
    """
    return await ws_handle.recv()


class SparkChatAi(ChatAI):
    """
    iFlytek Spark Chat AI implementation.

    This class provides the main interface for interacting with iFlytek's Spark Chat API,
    handling WebSocket connections, message streaming, and response processing.
    """

    spark_version: str
    patch_id: list = []

    model_config = {"arbitrary_types_allowed": True, "protected_namespaces": ()}

    def token_calculation(self, text: str) -> int:
        """
        Calculate the number of tokens in the given text.

        :param text: Input text to calculate tokens for
        :return: Number of tokens
        """
        raise NotImplementedError

    def image_processing(self, image_path: str) -> Any:
        """
        Process image data.

        :param image_path: Path to the image file
        :return: Processed image result
        """
        raise NotImplementedError

    def assemble_url(self, span: Span) -> str:
        """
        Assemble the authenticated URL for Spark Chat API.

        :param span: Tracing span for logging
        :return: Authenticated WebSocket URL
        """
        if not self.model_url:
            spark_host = os.getenv("SPARK_HOST", "ws://spark-api.xf-yun.com")
            spark_path = spark_mapping.get(self.model_name, None)
            if not spark_path:
                raise CustomException(
                    err_code=CodeEnum.SPARK_REQUEST_ERROR,
                    err_msg="Request URL is empty",
                    cause_error="Request URL is empty",
                )
            self.model_url = f"{spark_host}/{spark_path}"
        url_auth = SparkChatHmacAuth(self.model_url, self.api_key, self.api_secret)
        span.add_info_events({"spark_url": self.model_url})
        url = url_auth.create_url()
        return url

    def assemble_payload(self, message: list, **kwargs: Any) -> str:
        """
        Assemble the payload for Spark Chat API request.

        :param message: List of conversation messages
        :param kwargs: Additional parameters including search_disable flag
        :return: JSON string payload for the API request
        """
        search_disable = kwargs.get("search_disable", True)
        chat = {
            "domain": self.model_name,
            "temperature": self.temperature,
            "max_tokens": self.max_tokens,
            "top_k": self.top_k,
            "auditing": "default",
        }
        if not search_disable:
            chat.update(
                {
                    "tools": [
                        {
                            "type": "web_search",
                            "web_search": {
                                "enable": True,
                                "show_ref_label": False,
                                "search_mode": "normal",
                            },
                        }
                    ]
                }
            )
        else:
            chat.update({"question_type": "not_knowledge"})
        header: Dict[str, Any] = {
            "app_id": self.app_id,
            "uid": self.uid,
        }
        if self.patch_id:
            header["patch_id"] = self.patch_id

        payload_data = {
            "header": header,
            "parameter": {"chat": chat},
            "payload": {"message": {"text": message}},
        }
        return json.dumps(payload_data, ensure_ascii=False)

    def decode_message(self, msg: dict) -> Tuple[int, int, str, str, Dict[str, Any]]:
        """
        Decode and extract information from Spark API response message.

        :param msg: Raw message dictionary from Spark API
        :return: Tuple containing (code, status, content, reasoning_content, token_usage)
        """
        code = msg["header"]["code"]
        status = msg["header"]["status"]
        resp_payload = msg["payload"]
        text = resp_payload["choices"]["text"][0]
        content = text.get("content", "")
        reasoning_content = text.get("reasoning_content", "")
        token_usage = (
            {}
            if status != SparkLLMStatus.END.value
            else msg["payload"]["usage"]["text"]
        )
        return code, status, content, reasoning_content, token_usage

    async def _recv_messages(
        self,
        ws_handle: websockets.WebSocketClientProtocol,
        timeout: float | None = None,
    ) -> AsyncIterator[Any]:
        """
        Receive messages from WebSocket connection with timeout handling.

        :param ws_handle: WebSocket client protocol handle
        :param timeout: Optional timeout in seconds for message reception
        :return: Async iterator yielding received messages
        """
        while True:
            try:
                if timeout is not None:
                    msg_json = await asyncio.wait_for(
                        recv_with_retry(ws_handle), timeout=timeout
                    )
                else:
                    msg_json = await recv_with_retry(ws_handle)
                yield msg_json
            except asyncio.TimeoutError as e:
                raise CustomException(
                    err_code=CodeEnum.SPARK_REQUEST_ERROR,
                    err_msg=f"LLM response timeout ({timeout}s)",
                    cause_error=f"LLM response timeout ({timeout}s)",
                ) from e
            except websockets.ConnectionClosed as err:
                # After RETRY_CNT retries, this will catch the final ConnectionClosed exception
                raise err
            except CustomException as err:
                raise err
            except Exception as err:
                raise CustomException(
                    err_code=CodeEnum.SPARK_REQUEST_ERROR,
                    err_msg=f"{str(err)}",
                    cause_error=f"{str(err)}",
                ) from err

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
        Asynchronously call the Spark Chat API.

        :param flow_id: Unique identifier for the workflow flow
        :param user_message: List of user messages for the conversation
        :param span: Tracing span for logging and monitoring
        :param extra_params: Additional parameters for the request
        :param timeout: Optional timeout for the request
        :param search_disable: Whether to disable web search functionality
        :param event_log_node_trace: Optional node trace logger
        :return: Async iterator yielding LLM response objects
        """
        url = self.assemble_url(span)
        payload = self.assemble_payload(user_message, search_disable=search_disable)
        # Customize quick/slow thinking behavior
        payload = await self._handle_quickly_think_req_body(
            flow_id=flow_id, body=payload
        )

        if event_log_node_trace:
            event_log_node_trace.append_config_data(json.loads(payload))
        span.add_info_events({"payload": payload})
        llm_first_token_cost: float = -1
        try:
            # TODO: Timeout set to 60s to solve the issue of slow first frame response from LLM
            async with websockets.connect(
                url,
                ping_interval=None,
                ping_timeout=None,
                timeout=60,
                close_timeout=1,
            ) as ws_handle:
                start_time = time.time()
                await ws_handle.send(payload)
                async for msg_json in self._recv_messages(ws_handle, timeout):
                    msg = json.loads(msg_json)
                    if llm_first_token_cost == -1:
                        llm_first_token_cost = round(time.time() - start_time, 2)
                        span.add_info_events(
                            {"llm first token cost: ": llm_first_token_cost}
                        )
                        if event_log_node_trace:
                            event_log_node_trace.set_node_first_cost_time(
                                llm_first_token_cost
                            )
                    span.add_info_events({"recv": json.dumps(msg, ensure_ascii=False)})
                    if event_log_node_trace:
                        event_log_node_trace.add_info_log(
                            json.dumps(msg, ensure_ascii=False)
                        )
                    yield LLMResponse(msg=msg)
        except websockets.ConnectionClosedError as conn_err:
            span.add_error_event(f"WebSocket connection error: {conn_err}")
            span.record_exception(conn_err)
            raise CustomException(
                err_code=CodeEnum.SPARK_REQUEST_ERROR,
                err_msg="WebSocket connection closed abnormally",
                cause_error=f"WebSocket connection closed abnormally, {conn_err}",
            ) from conn_err
        except websockets.WebSocketException as err:
            span.add_error_event(f"WebSocket exception: {err}")
            span.record_exception(err)
            raise CustomException(
                err_code=CodeEnum.SPARK_REQUEST_ERROR,
                err_msg="WebSocket connection exception",
                cause_error=f"WebSocket connection exception, {err}",
            )
        except Exception as e:
            span.record_exception(e)
            raise e

    async def _handle_quickly_think_req_body(self, flow_id: str, body: str) -> str:
        """
        Handle quick thinking configuration for specific flows and models.

        :param flow_id: Unique identifier for the workflow flow
        :param body: Request body JSON string
        :return: Modified request body with thinking configuration
        """
        quickly_think_flow_ids = os.getenv("QUICKLY_THINK_FLOW_IDS", "").split(",")
        quickly_think_models = os.getenv("QUICKLY_THINK_MODELS", "").split(",")
        quickly_think_apps = os.getenv("QUICKLY_THINK_APPS", "").split(",")

        if flow_id in quickly_think_flow_ids or self.app_id in quickly_think_apps:
            if self.model_name not in quickly_think_models:
                return body
            body_dict = json.loads(body)
            body_dict["parameter"]["chat"]["enable_thinking"] = False
            return json.dumps(body_dict, ensure_ascii=False)
        return body
