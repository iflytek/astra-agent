import copy
import json
import re
from typing import Any, Dict

from jsonschema import ValidationError, validate  # type: ignore
from loguru import logger
from pydantic import BaseModel, Field
from workflow.engine.callbacks.callback_handler import ChatCallBacks
from workflow.engine.callbacks.openai_types_sse import GenerateUsage
from workflow.engine.entities.history import History
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseLLMNode
from workflow.engine.nodes.decision.prompt_v1_0 import (
    prompt_template,
    system_prompt_template,
)
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.util.prompt import PromptUtils, process_prompt
from workflow.exception.e import CustomException
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.providers.llm.iflytek_spark.schemas import Function
from workflow.infra.providers.llm.iflytek_spark.spark_fc_llm import SparkFunctionCallAi


def _replace_new_line(match: re.Match[str]) -> str:
    """
    Replace newline characters and quotes in JSON strings with escaped versions.

    :param match: Regex match object containing the action_input field
    :return: String with properly escaped characters for JSON format
    """
    value = match.group(2)
    value = re.sub(r"\n", r"\\n", value)
    value = re.sub(r"\r", r"\\r", value)
    value = re.sub(r"\t", r"\\t", value)
    value = re.sub(r'(?<!\\)"', r"\"", value)

    return match.group(1) + value + match.group(3)


def _custom_parser(multiline_string: str) -> str:
    """
    Parse and sanitize multiline strings from LLM responses for JSON compatibility.

    The LLM response for `action_input` may be a multiline string containing
    unescaped newlines, tabs or quotes. This function replaces those characters
    with their escaped counterparts for proper JSON formatting.

    :param multiline_string: Raw string from LLM response that may contain unescaped characters
    :return: Sanitized string with properly escaped characters for JSON parsing
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


class IntentChain(BaseModel):
    """
    Intent chain.
    :param id: ID of the intent chain
    :param name: Human-readable name for the intent chain
    :param description: Description of the intent chain
    :param intent_type: Type of the intent chain
    """

    id: str = Field(..., pattern=r"^intent-one-of::[0-9a-zA-Z-]+")
    name: str = Field(..., min_length=1)
    description: str = Field(..., min_length=1)
    intent_type: int = Field(..., alias="intentType", ge=1, le=2)


class DecisionNode(BaseLLMNode):
    """
    Decision node for workflow routing based on user input and intent classification.

    This node supports multiple execution modes including function call, prompt-based,
    and normal classification to determine the next workflow path based on user intent.
    """

    promptPrefix: str = Field(default="")  # Custom prompt prefix for decision making
    reasonMode: int = Field(
        default=0
    )  # Mode for reasoning (0: normal, 1: prompt-based)
    fs_params: object = {
        "type": "object",
        "properties": {
            "next_inputs": {"type": "string", "description": "User input content"}
        },
        "required": ["next_inputs"],
    }  # Function call parameters schema
    useFunctionCall: bool = Field(default=True)  # Whether to use function call mode
    question_type: str = Field(
        default="not_knowledge"
    )  # Type of question for LLM processing
    intentChains: list[IntentChain] = Field(
        ..., default_factory=list
    )  # List of intent chain configurations

    async def async_execute_fc(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
    ) -> NodeRunResult:
        """
        Execute decision node using function call mode for intent classification.

        This method uses Spark Function Call AI to classify user input and determine
        the appropriate intent chain based on predefined function schemas.

        :param variable_pool: Pool of variables available to the node
        :param span: Tracing span for monitoring
        :param event_log_node_trace: Optional event logging for node execution
        :return: NodeRunResult containing the selected intent and execution details
        """
        # Initialize function call instances and mapping
        fs_instances = []
        id_map = {}
        default_id = ""

        # Build function call schemas from intent chains
        for intent_chain in self.intentChains:
            # Create function schema for each intent chain
            fs_instance = Function(
                name=intent_chain.name,
                description=intent_chain.description,
                parameters=copy.deepcopy(self.fs_params),
            )
            # Map intent names to their IDs for routing
            id_map.update({intent_chain.name: intent_chain.id})
            # Set default ID for fallback intent (intentType == 1)
            default_id = (
                intent_chain.id if intent_chain.intent_type == 1 else default_id
            )
            fs_instances.append(fs_instance)
        # Log function call schemas for debugging
        span.add_info_events({"fs_schema": str(fs_instances)})

        # Get user input from variable pool
        usr_input = variable_pool.get_variable(
            node_id=self.node_id,
            key_name=self.input_identifier[0],
            span=span,
        )
        # Initialize Spark Function Call AI client
        fc_ai = SparkFunctionCallAi(
            model_url=self.url,
            model_name=self.domain,
            spark_version="",
            temperature=self.temperature,
            app_id=self.appId,
            api_key=self.apiKey,
            api_secret=self.apiSecret,
            max_tokens=self.maxTokens,
            top_k=self.topK,
            patch_id=self.patch_id,
            uid=self.uid,
            question_type=self.question_type,
            function_choice="",
        )
        # Process prompt prefix and variable replacements
        prompt_prefix = copy.deepcopy(self.promptPrefix)
        span.add_info_events({"user_input_prompt_prefix": prompt_prefix})

        # Find variables that need to be replaced in the prompt
        available_placeholders = PromptUtils.get_available_placeholders(
            self.node_id, prompt_prefix, variable_pool, span
        )
        replacements = {}
        # Replace variables in prompt with actual values
        try:
            for var_name in available_placeholders:
                var_name_list = re.split(r"[\[.\]]", var_name)
                # Only process variables that are in input identifiers
                if var_name_list[0].strip() in self.input_identifier:
                    replacements.update(
                        {
                            var_name: process_prompt(
                                node_id=self.node_id,
                                key_name=var_name,
                                variable_pool=variable_pool,
                                span=span,
                            )
                        }
                    )
        except CustomException as err:
            # Handle variable processing errors
            span.record_exception(err)
            run_result = NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=err,
            )
            return run_result
        # Ensure user input is string and apply variable replacements
        if not isinstance(usr_input, str):
            usr_input = str(usr_input)
        replacements_str = {
            k: (lambda v: (str(v) or " "))(v) for k, v in replacements.items()
        }
        prompt_prefix = PromptUtils.replace_variables(prompt_prefix, replacements_str)
        span.add_info_events({"finally_prompt_prefix": prompt_prefix})
        # Execute function call with Spark AI
        try:
            name, token_usage, _ = await fc_ai.async_call_spark_fc(
                user_input=prompt_prefix,
                event_log_node_trace=event_log_node_trace,
                function=fs_instances,
                span=span,
            )
            # Return successful result with selected intent
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                process_data={"query": usr_input},
                inputs={self.input_identifier[0]: usr_input},
                raw_output=str(name),
                outputs={self.output_identifier[0]: str(name)},
                edge_source_handle=id_map.get(name, default_id),
                token_cost=GenerateUsage(
                    completion_tokens=token_usage.get("completion_tokens", 0),
                    prompt_tokens=token_usage.get("prompt_tokens", 0),
                    total_tokens=token_usage.get("total_tokens", 0),
                ),
            )
        except Exception as err:
            # Handle execution errors by falling back to default intent
            span.add_error_event(f"{err}")
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                process_data={"query": usr_input},
                inputs={self.input_identifier[0]: usr_input},
                raw_output="DEFAULT",
                outputs={self.output_identifier[0]: "DEFAULT"},
                edge_source_handle=default_id,
                token_cost=GenerateUsage(),
            )

    async def async_execute_prompt(
        self,
        variable_pool: VariablePool,
        span: Span,
        flow_id: str,
        event_log_node_trace: NodeLog | None = None,
    ) -> NodeRunResult:
        """
        Execute decision node using prompt-based mode for intent classification.

        This method uses structured prompts with JSON output format to classify
        user input and determine the appropriate intent chain based on categories.

        :param variable_pool: Pool of variables available to the node
        :param span: Tracing span for monitoring
        :param flow_id: Unique identifier for the workflow
        :param event_log_node_trace: Optional event logging for node execution
        :return: NodeRunResult containing the selected intent and execution details
        """
        raw_output = ""
        try:
            # Process user input and build input dictionary
            input_dict = {}
            input = ""
            for input_key in self.input_identifier:
                # Get user input values from the variable pool
                input_value = variable_pool.get_variable(
                    node_id=self.node_id, key_name=input_key, span=span
                )
                input += str(input_value)
                # Store input values for downstream nodes
                input_dict.update({input_key: input_value})

            # Prepare prompt prefix and instructions
            prompt_prefix = copy.deepcopy(self.promptPrefix)
            instructions = (
                prompt_prefix
                if prompt_prefix
                else "Classify text based on user input and provided classification information"
            )

            # Build categories and ID mapping from intent chains
            categories: list[dict[str, str]] = []
            idMap: Dict[str, Any] = {}
            defalut_id = ""
            for p in self.intentChains:
                if p.intent_type != 1:
                    # Regular intent category
                    idMap[p.name] = p.id
                    categories.append(
                        {
                            "category_id": p.id,
                            "category_name": p.name,
                            "category_desc": p.description,
                        }
                    )
                else:
                    # Default fallback intent
                    defalut_id = str(p.id)
                    categories.append(
                        {
                            "category_id": p.id,
                            "category_name": "DEFAULT",
                            "category_desc": "Default intent when no other intent matches",
                        }
                    )
                    idMap["DEFAULT"] = p.id
            destinations_dict = {
                "input_text": input,
                "categories": categories,
                "classification_instructions": [instructions],
            }
            destinations = json.dumps(destinations_dict, ensure_ascii=False)
            # 1. Replace the {destinations} placeholder in the system template
            router_template = system_prompt_template.replace(
                "{{destinations}}", destinations
            )

            # 2. Replace the {{histories}} placeholder with content from the previous node input
            user_input_template = router_template.replace("{{histories}}", "")

            history_v2 = None
            history_chat = (
                variable_pool.get_history(self.node_id)
                if self.enableChatHistory
                else None
            )

            # End-to-end chat history support
            if self.enableChatHistoryV2 and self.enableChatHistoryV2.is_enabled:
                # Disable legacy chat history when v2 is enabled
                history_chat = None
                # Configure history parameters: max_token and rounds
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

            token_usage, res, _, processed_history = await self._chat_with_llm(
                span=span,
                flow_id=flow_id,
                history_chat=history_chat,
                history_v2=history_v2,
                prompt_template=user_input_template,
                variable_pool=variable_pool,
                event_log_node_trace=event_log_node_trace,
            )
            # Attach processed chat history to inputs for front-end debugging
            if processed_history:
                input_dict.update({"chatHistory": processed_history})

            # Persist raw_output for downstream use and debugging
            raw_output = res
            match = re.search(r"```(json)?(.*)```", res, re.DOTALL)
            json_str = res if match is None else match.group(2)
            json_str = _custom_parser(json_str.strip())
            span.add_info_event(f"json_str: {json_str}")
            result = json.loads(json_str)

            schema = {
                "type": "object",
                "required": ["category_name"],
                "properties": {"category_name": {"type": "string"}},
            }

            # Validate the result using JSON Schema
            try:
                validate(instance=result, schema=schema)
            except ValidationError as e:
                # JSON does not pass schema validation
                logger.debug(f"JSON data does not pass schema validation: {e.message}")

            edge_source_handle = idMap.get(result["category_name"], defalut_id)
            category_name = (
                result["category_name"]
                if edge_source_handle != defalut_id
                else "DEFAULT"
            )

            outputs = {self.output_identifier[0]: category_name}

            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                process_data={"query": user_input_template},
                inputs=input_dict,
                raw_output=str(raw_output),
                outputs=outputs,
                edge_source_handle=edge_source_handle,
                token_cost=GenerateUsage(
                    completion_tokens=token_usage.get("completion_tokens", 0),
                    prompt_tokens=token_usage.get("prompt_tokens", 0),
                    total_tokens=token_usage.get("total_tokens", 0),
                ),
            )
        except Exception as err:
            span.record_exception(err)
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                process_data={"query": user_input_template},
                inputs=input_dict,
                raw_output=str(raw_output),
                outputs={self.output_identifier[0]: "DEFAULT"},
                edge_source_handle=defalut_id,
                token_cost=GenerateUsage(),
            )

    async def async_execute_normal(
        self,
        variable_pool: VariablePool,
        span: Span,
        flow_id: str,
        event_log_node_trace: NodeLog | None = None,
    ) -> NodeRunResult:
        """
        Execute decision node in normal (non-function-call, non-structured-prompt) mode.

        The model receives a constructed prompt including candidate destinations and
        returns a JSON object with the selected destination and optional next inputs.

        :param variable_pool: Pool of variables available to the node
        :param span: Tracing span for monitoring
        :param flow_id: Unique identifier for the workflow
        :param event_log_node_trace: Optional event logging for node execution
        :return: NodeRunResult containing the selected intent and execution details
        """
        raw_output = ""
        try:
            # Build the complete input string by concatenating all declared inputs
            # 1. Read user-provided inputs
            input_dict = {}
            input = ""
            for input_key in self.input_identifier:
                # 2. Read each variable and append to the input string
                input_value = variable_pool.get_variable(
                    node_id=self.node_id, key_name=input_key, span=span
                )
                input += str(input_value)
                # 3. Record inputs for downstream nodes
                input_dict.update({input_key: input_value})

            # Build a mapping from intent name to next node id (and find default id)
            idMap = {}
            defalut_id = ""
            for p in self.intentChains:
                if p.intent_type != 1:
                    idMap[p.name] = p.id
                else:
                    defalut_id = str(p.id)

            # Construct the prompt using the predefined template
            # 0. Prepare intent descriptions and names
            destinations_list: list = []
            # Collect all non-default intents to send to the model
            destinations_list.extend(
                f"{p.name}: {p.description}"
                for p in self.intentChains
                if p.intent_type != 1
            )

            destinations_str = "\n".join(destinations_list)

            # 1. Replace the {destinations} placeholder
            router_template = prompt_template.replace(
                "{destinations}", destinations_str
            )

            # 2. Replace {{__input__}} with the concatenated input from the previous node(s)
            router_template = router_template.replace("{{__input__}}", str(input))

            # 3. Prepend the custom promptPrefix if provided
            user_input_template = self.promptPrefix + "\n" + router_template

            # Send the constructed prompt to the LLM
            history_v2 = None
            history_chat = (
                variable_pool.get_history(self.node_id)
                if self.enableChatHistory
                else None
            )

            # End-to-end chat history support
            if self.enableChatHistoryV2 and self.enableChatHistoryV2.is_enabled:
                # Disable legacy chat history when v2 is enabled
                history_chat = None
                # Configure history parameters: max_token and rounds
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

            token_usage, res, _, processed_history = await self._chat_with_llm(
                span=span,
                flow_id=flow_id,
                history_chat=history_chat,
                history_v2=history_v2,
                prompt_template=user_input_template,
                variable_pool=variable_pool,
                event_log_node_trace=event_log_node_trace,
            )
            # Attach processed chat history to inputs for front-end debugging
            if processed_history:
                input_dict.update({"chatHistory": processed_history})

            # Persist raw_output for downstream use and debugging
            raw_output = res
            match = re.search(r"```(json)?(.*)```", res, re.DOTALL)
            json_str = res if match is None else match.group(2)
            json_str = json_str.strip()
            json_str = _custom_parser(json_str)
            result = json.loads(json_str)

            schema = {
                "type": "object",
                "required": ["destination", "next_inputs"],
                "properties": {
                    "destination": {"type": "string"},
                    "next_inputs": {"type": "string"},
                },
            }

            # Validate the result using JSON Schema
            try:
                validate(instance=result, schema=schema)
            except ValidationError as e:
                # JSON does not pass schema validation
                logger.debug(f"JSON data does not pass schema validation: {e.message}")

            outputs = {self.output_identifier[0]: result["destination"]}
            edge_source_handle = (
                defalut_id
                if result["destination"] == "DEFAULT"
                else str(idMap[result["destination"]])
            )

            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                process_data={"query": user_input_template},
                inputs=input_dict,
                raw_output=str(raw_output),
                outputs=outputs,
                edge_source_handle=edge_source_handle,
                token_cost=GenerateUsage(
                    completion_tokens=token_usage.get("completion_tokens", 0),
                    prompt_tokens=token_usage.get("prompt_tokens", 0),
                    total_tokens=token_usage.get("total_tokens", 0),
                ),
            )

        except Exception as err:
            span.record_exception(err)
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                process_data={"query": user_input_template},
                inputs=input_dict,
                raw_output=str(raw_output),
                outputs={self.output_identifier[0]: "DEFAULT"},
                edge_source_handle=defalut_id,
            )

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Orchestrate decision node execution by selecting the appropriate mode.

        When reasonMode equals 1, the prompt-based path is used. If function-call
        mode is enabled, the function-call path is selected. Otherwise, normal
        mode is executed.

        :param variable_pool: Pool of variables available to the node
        :param span: Tracing span for monitoring
        :param event_log_node_trace: Optional event logging for node execution
        :param kwargs: Extra keyword arguments (e.g., callbacks containing flow_id)
        :return: NodeRunResult produced by the selected execution path
        """
        callbacks: ChatCallBacks = kwargs.get("callbacks", None)

        if self.reasonMode == 1:
            return await self.async_execute_prompt(
                variable_pool=variable_pool,
                span=span,
                flow_id=callbacks.flow_id if callbacks else "",
                event_log_node_trace=event_log_node_trace,
            )

        if self.useFunctionCall:
            return await self.async_execute_fc(
                variable_pool=variable_pool,
                span=span,
                event_log_node_trace=event_log_node_trace,
            )
        return await self.async_execute_normal(
            variable_pool=variable_pool,
            span=span,
            flow_id=callbacks.flow_id if callbacks else "",
            event_log_node_trace=event_log_node_trace,
        )
