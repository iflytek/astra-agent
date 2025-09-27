import asyncio
import copy
import json
import os
import time
from typing import Any, Dict, Optional, Tuple

import aiohttp
from aiohttp import ClientTimeout
from pydantic import Field
from workflow.consts.engine.chat_status import ChatStatus
from workflow.domain.models.ai_app import App
from workflow.engine.callbacks.openai_types_sse import GenerateUsage
from workflow.engine.entities.history import EnableChatHistoryV2, History
from workflow.engine.entities.msg_or_end_dep_info import MsgOrEndDepInfo
from workflow.engine.entities.node_entities import NodeType
from workflow.engine.entities.output_mode import EndNodeOutputModeEnum
from workflow.engine.entities.variable_pool import ParamKey, VariablePool
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.middleware.database.utils import session_getter
from workflow.extensions.middleware.getters import get_db_service
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span


class FlowNode(BaseNode):
    """
    Flow node implementation for executing nested workflows.

    This node allows a workflow to call another workflow as a sub-process,
    enabling workflow composition and reusability.
    """

    # Flow configuration parameters
    flowId: str = Field(..., min_length=1)  # Target flow ID to execute
    appId: str = Field(..., min_length=1)  # Application ID for authentication
    uid: str = Field(..., min_length=1)  # User ID for the flow execution

    # Chat history configuration for conversation context
    enableChatHistoryV2: EnableChatHistoryV2 = Field(
        default_factory=EnableChatHistoryV2
    )

    # Default chat body template for API requests
    chatBody: dict = {
        "inputs": {},
        "appId": "xxxx",
        "uid": "xxxx",
        "caller": "workflow",
        "botId": "xxxxxxxx",
    }

    # Optional version specification for the target flow
    version: Optional[str] = None

    def assemble_chat_body(self, inputs: dict) -> dict:
        """
        Assemble the chat body for API requests.

        Creates a deep copy of the default chat body template and updates it
        with the current node configuration and input parameters.

        :param inputs: Input parameters to include in the chat body
        :return: Assembled chat body dictionary for API requests
        """
        chat_body: dict = copy.deepcopy(self.chatBody)
        chat_body.update(
            {
                "appId": self.appId,
                "uid": self.uid,
                "botId": self.flowId,
            }
        )
        chat_body["inputs"].update(inputs)
        return chat_body

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Execute the flow node asynchronously.

        This method orchestrates the execution of a nested workflow by:
        1. Collecting input variables from the variable pool
        2. Making an API call to the target workflow via SSE
        3. Processing the response and extracting outputs
        4. Returning execution results with token usage information

        :param variable_pool: Variable pool containing workflow variables
        :param span: Tracing span for observability
        :param event_log_node_trace: Optional node trace logging
        :param kwargs: Additional keyword arguments including message dependencies
        :return: NodeRunResult containing execution status and outputs
        """
        try:
            # Extract message dependencies for streaming output
            msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo] = kwargs.get(
                "msg_or_end_node_deps", {}
            )

            # Collect input variables from the variable pool
            inputs = {}
            for input_key in self.input_identifier:
                input_value = variable_pool.get_variable(
                    node_id=self.node_id, key_name=input_key, span=span
                )
                inputs.update({input_key: input_value})

            # Initialize output containers
            outputs: dict[Any, Any] = {}
            token_usage: dict[Any, Any] = {}

            # Get the workflow SSE endpoint URL
            sparkflow_url_sse = os.getenv(
                "WORKFLOW_URL_SSE",
                "https://xingchen-api.xf-yun.com/workflow/v1/chat/completions",
            )

            # Execute the nested workflow via SSE API
            outputs, token_usage = await self.req_flow_api_with_see(
                sparkflow_url_sse,
                inputs,
                variable_pool,
                span,
                event_log_node_trace=event_log_node_trace,
                msg_or_end_node_deps=msg_or_end_node_deps,
            )

            # Order outputs according to the defined output identifiers
            order_outputs = {}
            for outputs_key in self.output_identifier:
                if outputs_key in outputs:
                    order_outputs.update({outputs_key: outputs.get(outputs_key)})
                else:
                    # Fallback to variable pool if not in direct outputs
                    value = variable_pool.get_output_variable(
                        node_id=self.node_id, key_name=outputs_key, span=span
                    )
                    order_outputs.update({outputs_key: value})
            # Return successful execution result with token usage
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                process_data={},
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=inputs,
                raw_output="",
                outputs=order_outputs,
                token_cost=GenerateUsage(
                    completion_tokens=token_usage.get("completion_tokens", 0),
                    prompt_tokens=token_usage.get("prompt_tokens", 0),
                    total_tokens=token_usage.get("total_tokens", 0),
                ),
            )
        except CustomException as err:
            # Handle custom workflow exceptions
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=err,
            )
        except Exception as err:
            # Handle unexpected exceptions and record in tracing
            span.record_exception(err)
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=CustomException(
                    CodeEnum.WORKFLOW_EXECUTION_ERROR,
                    cause_error=err,
                ),
            )

    async def req_flow_api_with_see(
        self,
        url: str,
        inputs: dict,
        variable_pool: VariablePool,
        span: Span,
        msg_or_end_node_deps: Dict[str, MsgOrEndDepInfo],
        event_log_node_trace: NodeLog | None = None,
    ) -> Tuple[dict, dict]:
        """
        Execute nested workflow via Server-Sent Events (SSE) API.

        This method establishes a streaming connection to the target workflow
        and processes the response in real-time, handling different output modes
        and streaming content to dependent nodes when necessary.

        :param url: SSE endpoint URL for the workflow API
        :param inputs: Input parameters for the target workflow
        :param variable_pool: Variable pool for workflow context
        :param span: Tracing span for observability
        :param msg_or_end_node_deps: Message dependencies for streaming output
        :param event_log_node_trace: Optional node trace logging
        :return: Tuple containing (outputs_dict, token_usage_dict)
        :raises CustomException: When workflow execution fails or times out
        """
        # Get the output mode configuration for the flow
        output_mode = variable_pool.system_params.get(
            ParamKey.FlowOutputMode, node_id=self.node_id
        )
        if output_mode is None:
            raise CustomException(
                err_code=CodeEnum.WORKFLOW_EXECUTION_ERROR,
                cause_error=f"Flow output mode not configured for flow_id: {self.flowId}",
            )

        # Assemble request headers and body
        headers, req_body = self._assemble_request(
            url, inputs, variable_pool, span, event_log_node_trace
        )

        # Initialize response containers
        outputs = {}
        token_usage = {}

        try:
            # Initialize content accumulators for streaming response
            result_content = ""
            result_reasoning_content = ""

            # Configure timeout based on retry settings
            interval_timeout = (
                self.retry_config.timeout if self.retry_config.should_retry else None
            )

            # Establish SSE connection with appropriate timeouts
            async with aiohttp.ClientSession(
                timeout=ClientTimeout(
                    total=30 * 60, sock_connect=30, sock_read=interval_timeout
                )
            ) as session:
                async with session.post(
                    url=url, headers=headers, json=req_body
                ) as response:
                    # Process streaming response line by line
                    async for line in response.content:
                        line_str = line.decode("utf-8")
                        if line_str == "\n":
                            continue

                        # Log received data for debugging
                        span.add_info_event(f"recv: {line_str}")

                        # Parse SSE data format
                        msg: dict[str, Any] = json.loads(line_str.removeprefix("data:"))

                        # Check for API errors
                        if msg.get("code", 0) != 0:
                            raise CustomException(
                                err_code=CodeEnum.WORKFLOW_EXECUTION_ERROR,
                                err_msg=msg.get("message", ""),
                                cause_error=json.dumps(msg, ensure_ascii=False),
                            )

                        # Extract choices from response
                        choices = msg.get("choices", ())
                        if not choices:
                            break

                        # Process content delta
                        delta = choices[0].get("delta", {})
                        content, reasoning_content = delta.get(
                            "content", ""
                        ), delta.get("reasoning_content", "")

                        # Accumulate content for final output
                        result_content += content
                        result_reasoning_content += reasoning_content

                        # Stream content to dependent nodes if in prompt mode
                        if output_mode == EndNodeOutputModeEnum.PROMPT_MODE.value:
                            await self.put_stream_content(
                                self.node_id,
                                variable_pool,
                                msg_or_end_node_deps,
                                NodeType.FLOW.value,
                                msg,
                            )

                        # Check for completion
                        if (
                            choices[0].get("finish_reason")
                            == ChatStatus.FINISH_REASON.value
                        ):
                            token_usage = msg.get("usage", {})
                            break
        except asyncio.TimeoutError as e:
            # Handle timeout errors with detailed information
            raise CustomException(
                err_code=CodeEnum.WORKFLOW_EXECUTION_ERROR,
                err_msg=f"Flow node response timeout ({interval_timeout}s)",
                cause_error=f"Flow node response timeout ({interval_timeout}s)",
            ) from e

        # Process outputs based on the configured output mode
        outputs = self._handle_outputs(
            output_mode, result_content, result_reasoning_content
        )
        return outputs, token_usage

    def _assemble_request(
        self,
        url: str,
        inputs: dict,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
    ) -> Tuple[dict, dict]:
        """
        Assemble HTTP request headers and body for workflow API call.

        This method constructs the complete request including authentication,
        chat history, and input parameters. It also queries the database
        to retrieve the application credentials for authentication.

        :param url: Target API endpoint URL
        :param inputs: Input parameters for the workflow
        :param variable_pool: Variable pool containing workflow context
        :param span: Tracing span for observability
        :param event_log_node_trace: Optional node trace logging
        :return: Tuple containing (headers_dict, request_body_dict)
        :raises CustomException: When app credentials are not found
        """
        # Initialize request headers
        headers = {"Content-Type": "application/json"}

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

        # Add chat history to inputs if available
        if history:
            inputs.update({"chatHistory": history})

        # Construct request body with flow parameters
        req_body = {
            "flow_id": self.flowId,
            "uid": self.uid,
            "parameters": inputs,
            "ext": {},
            "stream": True,
            "history": history,
        }

        # Add version if specified
        if self.version:
            req_body.update({"version": self.version})

        # Log request details for debugging
        if event_log_node_trace:
            event_log_node_trace.append_config_data(
                {
                    "url": url,
                    "req_headers": headers,
                    "req_body": json.dumps(req_body, ensure_ascii=False),
                }
            )

        # Query application credentials from database
        with session_getter(get_db_service()) as session:
            start_time = time.time() * 1000
            app = session.query(App).filter_by(alias_id=self.appId).first()

            # Log database query performance
            span.add_info_events(
                {
                    "flow_node_get_appid_from_database": f"{time.time() * 1000 - start_time}"
                }
            )

            # Validate app existence
            if not app or app.id == 0:
                raise CustomException(
                    err_code=CodeEnum.APP_NOT_FOUND_ERROR,
                    err_msg=f"App not found for nested workflow execution: {self.appId}",
                )

            # Construct authorization header
            authorization = f"Bearer {app.api_key}:{app.api_secret}"

        # Set appropriate authentication header based on environment
        if "127.0.0.1" in url or "dev" in url or "pre" in url or "10.1.87.65" in url:
            # Use consumer username for local/development environments
            headers["X-Consumer-Username"] = self.appId
        else:
            # Use bearer token for production environments
            headers["Authorization"] = authorization

        return headers, req_body

    def _handle_outputs(
        self, output_mode: int, result_content: str, result_reasoning_content: str
    ) -> dict:
        """
        Process workflow outputs based on the configured output mode.

        Different output modes handle the response content differently:
        - VARIABLE_MODE: Parse JSON content as structured variables
        - OLD_PROMPT_MODE: Return content as a single output
        - PROMPT_MODE: Return both content and reasoning content

        :param output_mode: Configured output mode for the workflow
        :param result_content: Main content from the workflow response
        :param result_reasoning_content: Reasoning content from the workflow response
        :return: Processed outputs dictionary
        :raises CustomException: When output mode is invalid or content parsing fails
        """
        outputs = {}

        if output_mode == EndNodeOutputModeEnum.VARIABLE_MODE.value:
            # Parse JSON content as structured workflow parameters
            try:
                outputs = json.loads(result_content)
            except Exception as e:
                raise CustomException(
                    err_code=CodeEnum.WORKFLOW_EXEC_RESP_FORMAT_ERROR,
                    cause_error=f"Workflow response format error. Response: {result_content}, "
                    f"Expected deserialization keys: {self.output_identifier}",
                ) from e
        elif output_mode == EndNodeOutputModeEnum.OLD_PROMPT_MODE.value:
            # Return content as a single output parameter
            outputs[self.output_identifier[0]] = result_content
        elif output_mode == EndNodeOutputModeEnum.PROMPT_MODE.value:
            # Return both content and reasoning content
            outputs["content"] = result_content
            outputs["reasoning_content"] = result_reasoning_content
        else:
            raise CustomException(
                err_code=CodeEnum.WORKFLOW_EXECUTION_ERROR,
                cause_error=f"Invalid workflow output mode: {output_mode}",
            )

        return outputs
