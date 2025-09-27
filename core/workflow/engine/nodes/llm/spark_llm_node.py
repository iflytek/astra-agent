import json
import os
import re
from typing import Any

from workflow.engine.callbacks.callback_handler import ChatCallBacks
from workflow.engine.callbacks.openai_types_sse import GenerateUsage
from workflow.engine.entities.history import History
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseLLMNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.llm.prompt_ai_personal import system_template
from workflow.engine.nodes.util.prompt import PromptUtils, process_prompt
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.providers.llm.iflytek_spark.const import RespFormatEnum


def _replace_new_line(match: re.Match[str]) -> str:
    """
    Replace newline characters and quotes in JSON strings with their escaped versions.

    :param match: Regular expression match object containing the JSON string
    :return: String with escaped newlines, carriage returns, tabs, and quotes
    """
    value = match.group(2)
    value = re.sub(r"\n", r"\\n", value)
    value = re.sub(r"\r", r"\\r", value)
    value = re.sub(r"\t", r"\\t", value)
    value = re.sub(r'(?<!\\)"', r"\"", value)

    return match.group(1) + value + match.group(3)


def _custom_parser(multiline_string: str) -> str:
    """
    Parse and escape multiline strings in LLM responses for JSON compatibility.

    The LLM response for `action_input` may be a multiline
    string containing unescaped newlines, tabs or quotes. This function
    replaces those characters with their escaped counterparts.
    (newlines in JSON must be double-escaped: `\\n`)

    :param multiline_string: Raw multiline string from LLM response
    :return: Properly escaped string suitable for JSON parsing
    """
    if isinstance(multiline_string, (bytes, bytearray)):
        multiline_string = multiline_string.decode()

    multiline_string = re.sub(
        r'("action_input"\:\s*")(.*)(")',
        _replace_new_line,
        multiline_string,
        flags=re.DOTALL,
    )

    return multiline_string


class SparkLLMNode(BaseLLMNode):
    """
    Spark LLM node implementation for workflow execution.

    This class handles the execution of Spark LLM (Xinghuo) model nodes,
    including prompt processing, history management, and response formatting.
    """

    def resp_format_text_parser(self, res: str, think_contents: str) -> dict:
        """
        Parse text format response from LLM.

        :param res: Raw response text from LLM
        :param think_contents: Reasoning/thinking content from LLM
        :return: Dictionary with parsed response data
        """
        if think_contents:
            resp = {}
            for output_key in self.output_identifier:
                if output_key == "REASONING_CONTENT":
                    resp["REASONING_CONTENT"] = think_contents
                else:
                    resp[output_key] = res
            return resp
        return {self.output_identifier[0]: res}

    def resp_format_markdown_parser(self, res: str) -> dict:
        """
        Parse markdown format response from LLM.

        :param res: Raw response text from LLM
        :return: Dictionary with parsed markdown response
        """
        return {self.output_identifier[0]: res}

    def resp_format_json_parser(self, res: str) -> dict:
        """
        Parse JSON format response from LLM.

        Supports JSON return mode with pattern matching and custom parsing.

        :param res: Raw response text from LLM
        :return: Dictionary with parsed JSON response
        """
        match = re.search(self.re_match_pattern, res, re.DOTALL)
        if match is None:
            json_str = res
        else:
            # If match found, use the content within the backticks
            json_str = match.group(2)
        json_str = json_str.strip()
        json_str = _custom_parser(json_str)
        try:
            res_json = json.loads(json_str)
        except Exception:
            return {}
        return res_json

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronously execute the LLM node.

        This method handles the complete execution flow including prompt processing,
        history management, LLM communication, and response formatting.

        :param variable_pool: Variable pool containing workflow variables
        :param span: Tracing span for monitoring
        :param event_log_node_trace: Optional node trace logging
        :param kwargs: Additional keyword arguments including callbacks and dependencies
        :return: Node execution result with outputs and metadata
        """
        callbacks: ChatCallBacks = kwargs.get("callbacks", None)
        msg_or_end_node_deps = kwargs.get("msg_or_end_node_deps", {})
        try:
            inputs = {}
            inputs.update(
                {
                    k: variable_pool.get_variable(
                        node_id=self.node_id, key_name=k, span=span
                    )
                    for k in self.input_identifier
                }
            )
            prompt_template = self.template
            system_prompt_template = self.systemTemplate
            prompt_template = self.get_full_prompt(prompt_template, span, variable_pool)

            history_v2 = None
            history_chat = (
                variable_pool.get_history(self.node_id)
                if self.enableChatHistory
                else None
            )

            if system_prompt_template is not None:
                system_prompt_template = self.get_full_prompt(
                    system_prompt_template, span, variable_pool
                )
            else:
                if self.domain == "xaipersonality":
                    system_prompt_template = system_template
                    if self.enableChatHistory:
                        history_chat = variable_pool.get_aipensonal_history(
                            self.node_id
                        )

            image_url = ""
            # Image understanding models configuration
            image_models = os.getenv("SPARK_IMAGE_MODEL_DOMAIN", "image,imagev3").split(
                ","
            )
            if self.domain in image_models:
                history_chat = None
                image_url = inputs.get("SYSTEM_IMAGE", "")

            # End-to-end history management
            if self.enableChatHistoryV2.is_enabled:
                # Disable old history mechanism
                history_chat = None
                # History parameters configuration: max_token, rounds
                rounds = self.enableChatHistoryV2.rounds
                max_token = self.maxTokens
                history_v2 = (
                    History(
                        origin_history=variable_pool.history_v2.origin_history,
                        max_token=max_token,
                        rounds=rounds,
                    )
                    if variable_pool.history_v2
                    else None
                )
            flow_id = callbacks.flow_id if callbacks else ""
            token_usage, res, think_contents, processed_history = (
                await self._chat_with_llm(
                    span=span,
                    flow_id=flow_id,
                    history_chat=history_chat,
                    history_v2=history_v2,
                    variable_pool=variable_pool,
                    prompt_template=prompt_template,
                    system_prompt_template=system_prompt_template,
                    event_log_node_trace=event_log_node_trace,
                    image_url=image_url,
                    stream=True,
                    msg_or_end_node_deps=msg_or_end_node_deps,
                )
            )

            # Add chat history to inputs for debug interface frontend parsing
            if processed_history:
                inputs.update({"chatHistory": processed_history})

            final_res = {
                RespFormatEnum.TEXT.value: lambda: self.resp_format_text_parser(
                    res, think_contents
                ),
                RespFormatEnum.MARKDOWN.value: lambda: self.resp_format_markdown_parser(
                    res
                ),
                RespFormatEnum.JSON.value: lambda: (
                    lambda d: (
                        d
                        if isinstance(d, dict)
                        else self.resp_format_text_parser(str(res), think_contents)
                        or d.update(
                            {
                                k: variable_pool.get_variable(
                                    node_id=self.node_id, key_name=k, span=span
                                )
                                for k in self.output_identifier
                                if k not in d
                            }
                        )
                        or d
                    )
                )(self.resp_format_json_parser(res)),
            }.get(self.respFormat, lambda: {})()

            order_outputs = {}
            order_outputs.update(
                {
                    output: final_res.get(output)
                    or variable_pool.get_variable(
                        node_id=self.node_id, key_name=output, span=span
                    )
                    for output in self.output_identifier
                }
            )
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                process_data={"query": prompt_template},
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=inputs,
                raw_output=res,
                outputs=order_outputs,
                token_cost=GenerateUsage(
                    completion_tokens=token_usage.get("completion_tokens", 0),
                    prompt_tokens=token_usage.get("prompt_tokens", 0),
                    total_tokens=token_usage.get("total_tokens", 0),
                ),
                # outputs=final_res
            )
        except CustomException as err:
            span.add_error_event(f"{err}")
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
                    CodeEnum.LLM_NODE_EXECUTION_ERROR,
                    cause_error=err,
                ),
            )

    def get_full_prompt(
        self, prompt_template: str, span_context: Span, variable_pool: VariablePool
    ) -> str:
        """
        Process and expand prompt template with variable substitutions.

        :param prompt_template: Raw prompt template string
        :param span_context: Tracing span for monitoring
        :param variable_pool: Variable pool containing available variables
        :return: Fully processed prompt with variable substitutions
        """
        available_placeholders = PromptUtils.get_available_placeholders(
            self.node_id, prompt_template, variable_pool, span_context
        )
        replacements = {}
        for var_name in available_placeholders:
            var_name_list = re.split(r"[\[.\]]", var_name)
            if var_name_list[0].strip() in self.input_identifier:
                replacements.update(
                    {
                        var_name: process_prompt(
                            node_id=self.node_id,
                            key_name=var_name,
                            variable_pool=variable_pool,
                            span=span_context,
                        )
                    }
                )
        replacements_str = {}
        for key, value in replacements.items():
            try:
                value = str(value)
                if len(value) == 0:
                    value = " "
            except Exception:
                value = " "
            replacements_str[key] = value
        # Replace variables in prompt template
        prompt_template = PromptUtils.replace_variables(
            prompt_template, replacements_str
        )
        return prompt_template
