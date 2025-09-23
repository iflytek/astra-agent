import copy
import json
import re
import traceback
from typing import Any, Dict

from workflow.engine.callbacks.callback_handler import ChatCallBacks
from workflow.engine.callbacks.openai_types_sse import GenerateUsage
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseLLMNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.params_extractor.prompt import pe_system_prompt
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.providers.llm.iflytek_spark.schemas import Function
from workflow.infra.providers.llm.iflytek_spark.spark_fc_llm import SparkFunctionCallAi
from workflow.utils.json_schema.json_schema_validator import JsonSchemaValidator


class ParamsExtractorNode(BaseLLMNode):
    """
    Parameter Extractor Node for extracting structured parameters from user input.

    This node uses LLM capabilities to extract structured parameters from natural language
    input based on predefined schemas. It supports both function calling and prompt-based
    extraction methods.
    """

    # Configuration parameters for the parameter extractor
    question_type: str = "not_knowledge"  # Type of question for LLM processing
    extractor_params: list = []  # List of parameters to extract
    reasonMode: int = 0  # Mode for reasoning (0: function calling, 1: prompt-based)
    instruction: str = ""  # Additional instructions for parameter extraction
    fc_schema_params: dict = {
        "type": "object",
        "properties": {},
        "required": [],
    }  # Function calling schema

    def get_node_config(self) -> Dict[str, Any]:
        """
        Get the configuration parameters for this node.

        :return: Dictionary containing all node configuration parameters
        """
        return {
            "model": self.model,
            "url": self.url,
            "domain": self.domain,
            "temperature": self.temperature,
            "appId": self.appId,
            "apiKey": self.apiKey,
            "apiSecret": self.apiSecret,
            "maxTokens": self.maxTokens,
            "uid": self.uid,
            "topK": self.topK,
            "patch_id": self.patch_id,
            "question_type": self.question_type,
            "extractor_params": self.extractor_params,
            "reasonMode": self.reasonMode,
            "instruction": self.instruction,
            "fc_schema_params": self.fc_schema_params,
        }

    def sync_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Synchronous execution method (not implemented).

        :param variable_pool: Pool of variables for the workflow
        :param span: Tracing span for monitoring
        :param event_log_node_trace: Optional node trace logging
        :param kwargs: Additional keyword arguments
        :return: Node execution result
        :raises NotImplementedError: This method is not implemented
        """
        raise NotImplementedError

    def assemble_schema_info(self) -> dict:
        """
        Assemble schema information for parameter extraction.

        Combines the base schema with additional extractor parameters to create
        a complete JSON schema for parameter extraction.

        :return: Complete JSON schema for parameter extraction
        """
        schema_content = copy.deepcopy(self.fc_schema_params)
        for extra_params in self.extractor_params:
            schema_content["properties"].update(
                {
                    extra_params["name"]: {
                        "type": extra_params.get("schema", {}).get("type"),
                        "description": extra_params.get("schema", {}).get(
                            "description"
                        ),
                    }
                }
            )
            if extra_params["required"]:
                schema_content["required"].append(extra_params["name"])
        return schema_content

    async def async_execute_prompt(
        self,
        variable_pool: VariablePool,
        span: Span,
        flow_id: str,
        event_log_node_trace: NodeLog | None = None,
    ) -> NodeRunResult:
        """
        Execute parameter extraction using prompt-based approach.

        This method uses a prompt template to instruct the LLM to extract parameters
        from user input and return them in JSON format.

        :param variable_pool: Pool of variables for the workflow
        :param span: Tracing span for monitoring
        :param flow_id: Unique identifier for the workflow flow
        :param event_log_node_trace: Optional node trace logging
        :return: Node execution result with extracted parameters
        """
        try:
            schema_content = self.assemble_schema_info()
            span.add_info_events(
                {"cs_schema": json.dumps(schema_content, ensure_ascii=False)}
            )

            usr_input = variable_pool.get_variable(
                node_id=self.node_id, key_name=self.input_identifier[0], span=span
            )
            if not isinstance(usr_input, str):
                usr_input = str(usr_input)

            user_prompt = (
                pe_system_prompt.replace("{{histories}}", "")
                .replace("{{instruction}}", self.instruction)
                .replace(
                    "{{json_structure}}", json.dumps(schema_content, ensure_ascii=False)
                )
                .replace("{{user_text}}", usr_input)
            )

            token_usage, res, _, _ = await self._chat_with_llm(
                span=span,
                flow_id=flow_id,
                variable_pool=variable_pool,
                prompt_template=user_prompt,
                event_log_node_trace=event_log_node_trace,
            )

            match = re.search(r"```(json)?(.*)```", res, re.DOTALL)
            if match is None:
                json_str = res
            else:
                json_str = match.group(2)

            extra_params = json.loads(json_str)

            res_dict = {}
            for output in self.output_identifier:
                if output not in extra_params:
                    res_dict.update(
                        {
                            output: variable_pool.get_variable(
                                node_id=self.node_id, key_name=output, span=span
                            )
                        }
                    )
                else:
                    res_dict.update({output: extra_params.get(output)})
            res_dict = self.schema_fixed_data(res_dict, variable_pool)

            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs={self.input_identifier[0]: usr_input},
                # outputs=order_outputs,
                outputs=res_dict,
                raw_output=res,
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                token_cost=GenerateUsage(
                    completion_tokens=token_usage.get("completion_tokens", 0),
                    prompt_tokens=token_usage.get("prompt_tokens", 0),
                    total_tokens=token_usage.get("total_tokens", 0),
                ),
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
                    CodeEnum.ExtractExecutionError,
                    cause_error=err,
                ),
            )

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Execute parameter extraction using the configured method.

        This method determines whether to use function calling or prompt-based
        extraction based on the reasonMode configuration.

        :param variable_pool: Pool of variables for the workflow
        :param span: Tracing span for monitoring
        :param event_log_node_trace: Optional node trace logging
        :param kwargs: Additional keyword arguments including callbacks
        :return: Node execution result with extracted parameters
        """
        if self.reasonMode == 1:
            callbacks: ChatCallBacks = kwargs.get("callbacks", None)
            return await self.async_execute_prompt(
                variable_pool=variable_pool,
                span=span,
                event_log_node_trace=event_log_node_trace,
                flow_id=callbacks.flow_id,
            )
        try:
            schema_content = self.assemble_schema_info()
            functions = [Function(parameters=schema_content)]
            for function in functions:
                span.add_info_events(
                    {"fc_schema": json.dumps(function.dict(), ensure_ascii=False)}
                )
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
            )
            usr_input = variable_pool.get_variable(
                node_id=self.node_id,
                key_name=self.input_identifier[0],
                span=span,
            )
            if not isinstance(usr_input, str):
                usr_input = str(usr_input)
            _, token_usage, res = await fc_ai.async_call_spark_fc(
                user_input=usr_input,
                event_log_node_trace=event_log_node_trace,
                function=functions,
                span=span,
            )
            extra_params = json.loads(res)
            res_dict = {}
            for output in self.output_identifier:
                if output not in extra_params:
                    res_dict.update(
                        {
                            output: variable_pool.get_variable(
                                node_id=self.node_id,
                                key_name=output,
                                span=span,
                            )
                        }
                    )
                else:
                    res_dict.update({output: extra_params.get(output)})
            order_outputs = {}
            for output in self.output_identifier:
                update_item = (
                    res_dict.get(output)
                    if output in res_dict
                    else variable_pool.get_variable(
                        node_id=self.node_id,
                        key_name=output,
                        span=span,
                    )
                )
                order_outputs.update({output: update_item})
            order_outputs = self.schema_fixed_data(order_outputs, variable_pool)
            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs={self.input_identifier[0]: usr_input},
                outputs=order_outputs,
                # outputs=res_dict,
                raw_output=res,
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                token_cost=GenerateUsage(
                    completion_tokens=token_usage.get("completion_tokens", 0),
                    prompt_tokens=token_usage.get("prompt_tokens", 0),
                    total_tokens=token_usage.get("total_tokens", 0),
                ),
            )
        except CustomException as err:
            traceback.print_exc()
            span.add_error_event(f"{err}")
            span.record_exception(err)
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=err,
            )
        except Exception as err:
            traceback.print_exc()
            span.add_error_event(f"{err}")
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=CustomException(
                    CodeEnum.ExtractExecutionError,
                    cause_error=err,
                ),
            )

    def schema_fixed_data(self, res_dict: dict, variable_pool: VariablePool) -> dict:
        """
        Validate and fix extracted data according to the schema.

        This method validates the extracted parameters against the defined schema
        and attempts to fix any validation errors automatically.

        :param res_dict: Dictionary containing extracted parameters
        :param variable_pool: Pool of variables for schema validation
        :return: Validated and fixed parameter dictionary
        """
        required = []
        schemas: dict = copy.deepcopy(variable_pool.validate_template)
        for mapping_key in variable_pool.output_variable_mapping.keys():
            if mapping_key.startswith(self.node_id):
                mapping_value = variable_pool.output_variable_mapping[mapping_key]
                value_schema = mapping_value.get("schema")
                key = mapping_key.split(f"{self.node_id}-")[-1]
                schemas["properties"].update({key: value_schema})
                if mapping_value.get("required", False):
                    required.append(key)
        if required:
            schemas.update({"required": required})
        validator = JsonSchemaValidator(schemas)
        is_valid, fixed_data = validator.validate_and_fix(res_dict)
        return fixed_data
