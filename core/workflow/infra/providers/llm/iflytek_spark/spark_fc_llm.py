"""
Function calling implementation for iFlytek Spark LLM provider.

This module provides functionality for function calling with the Spark API,
allowing the LLM to invoke predefined functions based on user input.
"""

import json
import os
from typing import Any, Dict, List, Optional

import websockets
from pydantic import BaseModel
from workflow.exception.e import CustomException
from workflow.exception.errors.code_convert import CodeConvert
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.providers.llm.iflytek_spark.const import spark_mapping
from workflow.infra.providers.llm.iflytek_spark.schemas import Function, SparkAiMessage
from workflow.infra.providers.llm.iflytek_spark.spark_chat_auth import SparkChatHmacAuth


class SparkFunctionCallAi(BaseModel):
    """
    iFlytek Spark Function Calling AI implementation.

    This class handles function calling interactions with the Spark API,
    allowing the LLM to invoke predefined functions based on user queries.
    """

    model_url: str
    model_name: str
    spark_version: str
    temperature: float
    app_id: str
    api_key: str
    api_secret: str
    max_tokens: int
    top_k: int
    uid: str
    patch_id: List[str] = []
    question_type: str = ""
    function_choice: str = "extractor_parameter"

    model_config = {"arbitrary_types_allowed": True, "protected_namespaces": ()}

    def assemble_url(self, span: Span) -> str:
        """
        Assemble the authenticated URL for Spark Function Call API.

        :param span: Tracing span for logging
        :return: Authenticated WebSocket URL
        """
        if not self.model_url:
            spark_path = spark_mapping.get(self.model_name, None)
            spark_host = os.getenv("SPARK_HOST", "ws://spark-api.xf-yun.com")
            if not spark_path:
                raise CustomException(
                    err_code=CodeEnum.SPARK_REQUEST_ERROR,
                    err_msg="Request URL is empty",
                    cause_error="Request URL is empty",
                )
            self.model_url = f"{spark_host}/{spark_path}"
        span.add_info_events({"spark_url": self.model_url})
        url_auth = SparkChatHmacAuth(self.model_url, self.api_key, self.api_secret)
        url = url_auth.create_url()
        return url

    def assemble_payload(self, message: list, function: list) -> str:
        """
        Assemble the payload for Spark Function Call API request.

        :param message: List of conversation messages
        :param function: List of available functions for the LLM to call
        :return: JSON string payload for the API request
        """
        payload_data: Dict[str, Any] = {
            "header": {"app_id": self.app_id, "uid": self.uid},
            "parameter": {
                "chat": {
                    "domain": self.model_name,
                    "temperature": self.temperature,
                    "max_tokens": self.max_tokens,
                    "top_k": self.top_k,
                    "auditing": "default",
                }
            },
            "payload": {"message": {"text": message}, "functions": {"text": function}},
        }
        if self.patch_id:
            payload_data["header"]["patch_id"] = self.patch_id
        if self.question_type:
            payload_data["parameter"]["chat"]["question_type"] = self.question_type
        if self.function_choice:
            payload_data["parameter"]["chat"]["function_choice"] = self.function_choice
        return json.dumps(payload_data, ensure_ascii=False)

    async def _recv_messages(
        self, ws_handle: websockets.WebSocketClientProtocol, span: Span
    ) -> tuple[str, dict, str]:
        """
        Receive and process function call messages from WebSocket.

        :param ws_handle: WebSocket client protocol handle
        :param span: Tracing span for logging
        :return: Tuple containing (function_name, token_usage, arguments)
        """
        while True:
            try:
                msg = json.loads(await ws_handle.recv())
                span.add_info_events(
                    {"function_call_recv": json.dumps(msg, ensure_ascii=False)}
                )
                code = msg["header"]["code"]
                if code != 0:
                    raise CustomException(
                        err_code=CodeConvert.sparkCode(code),
                        cause_error=json.dumps(msg, ensure_ascii=False),
                    )
                status = msg["header"]["status"]
                llm_service_sid = msg["header"]["sid"]
                # Check if it's a quick repair: if the last character of sid is '1', it's a quick repair
                if llm_service_sid[-1] == "1":
                    raise CustomException(
                        err_code=CodeEnum.SPARK_QUICK_REPAIR_ERROR,
                        err_msg="Sensitive content detected, LLM did not find function_call field",
                        cause_error="Sensitive content detected, LLM did not find function_call field",
                    )
                if status != 2:
                    continue
                token_usage = msg["payload"]["usage"]["text"]
                if "function_call" not in msg["payload"]["choices"]["text"][0]:
                    raise CustomException(
                        err_code=CodeEnum.SPARK_FUNCTION_NOT_CHOICE_ERROR,
                        err_msg="Cannot find function_call field in LLM response",
                        cause_error="Cannot find function_call field in LLM response",
                    )

                name = msg["payload"]["choices"]["text"][0]["function_call"]["name"]
                arguments = msg["payload"]["choices"]["text"][0]["function_call"][
                    "arguments"
                ]
                return name, token_usage, arguments
            except websockets.ConnectionClosed:
                raise CustomException(
                    err_code=CodeEnum.SPARK_REQUEST_ERROR,
                    err_msg="WebSocket connection closed",
                    cause_error="WebSocket connection closed",
                )
            except Exception as e:
                raise CustomException(
                    err_code=CodeEnum.SPARK_REQUEST_ERROR,
                    err_msg=f"{e}",
                    cause_error=f"{e}",
                )

    def _process_message(
        self, msg: dict, span: Span
    ) -> tuple[str | None, dict | None, str | None]:
        """
        Process a single function call message from the API response.

        :param msg: Message dictionary from Spark API
        :param span: Tracing span for logging
        :return: Tuple containing (function_name, token_usage, arguments) or (None, None, None) if not ready
        """
        span.add_info_events(
            {"function_call_recv": json.dumps(msg, ensure_ascii=False)}
        )
        code = msg["header"]["code"]
        if code != 0:
            raise CustomException(
                err_code=CodeConvert.sparkCode(code),
                cause_error=json.dumps(msg, ensure_ascii=False),
            )
        status = msg["header"]["status"]
        llm_service_sid = msg["header"]["sid"]
        # Check if it's a quick repair: if the last character of sid is '1', it's a quick repair
        if llm_service_sid[-1] == "1":
            raise CustomException(
                err_code=CodeEnum.SPARK_QUICK_REPAIR_ERROR,
                err_msg="Sensitive content detected, LLM did not find function_call field",
                cause_error="Sensitive content detected, LLM did not find function_call field",
            )
        if status != 2:
            return None, None, None
        token_usage = msg["payload"]["usage"]["text"]
        if "function_call" not in msg["payload"]["choices"]["text"][0]:
            raise CustomException(
                err_code=CodeEnum.SPARK_FUNCTION_NOT_CHOICE_ERROR,
                err_msg="Cannot find function_call field in LLM response",
                cause_error="Cannot find function_call field in LLM response",
            )

        name = msg["payload"]["choices"]["text"][0]["function_call"]["name"]
        arguments = msg["payload"]["choices"]["text"][0]["function_call"]["arguments"]
        return name, token_usage, arguments

    async def async_call_spark_fc(
        self,
        user_input: str,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        history: Optional[list[SparkAiMessage]] = None,
        function: Optional[list[Function]] = None,
    ) -> tuple[str, dict, str]:
        """
        Asynchronously call the Spark Function Call API.

        :param user_input: User's input message
        :param span: Tracing span for logging
        :param event_log_node_trace: Optional node trace logger
        :param history: Optional conversation history
        :param function: Optional list of available functions
        :return: Tuple containing (function_name, token_usage, arguments)
        """
        url = self.assemble_url(span)
        span.add_info_events({"user_input": user_input})
        usr_message = []
        if history:
            for h in history:
                usr_message.append(h.dict())
            span.add_info_events(
                {"history": json.dumps(usr_message, ensure_ascii=False)}
            )
        fc_message = []
        if function:
            for fc in function:
                fc_message.append(fc.dict())
        usr_message.append({"role": "user", "content": user_input})
        payload = self.assemble_payload(usr_message, fc_message)
        if event_log_node_trace:
            event_log_node_trace.append_config_data(json.loads(payload))

        try:
            async with websockets.connect(
                url, ping_interval=None, ping_timeout=None
            ) as ws_handle:
                await ws_handle.send(payload)
                span.add_info_events({"function_call_send": payload})
                return await self._recv_messages(ws_handle, span)
        except websockets.ConnectionClosedError as conn_err:
            span.add_error_event(f"WebSocket connection error: {conn_err}")
            raise CustomException(
                err_code=CodeEnum.SPARK_REQUEST_ERROR,
                err_msg=f"WebSocket connection closed abnormally, {conn_err}",
                cause_error=f"WebSocket connection closed abnormally, {conn_err}",
            )
        except websockets.WebSocketException as err:
            span.add_error_event(f"WebSocket exception: {err}")
            raise CustomException(
                err_code=CodeEnum.SPARK_REQUEST_ERROR,
                err_msg=f"WebSocket connection exception, {err}",
                cause_error=f"WebSocket connection exception, {err}",
            )
