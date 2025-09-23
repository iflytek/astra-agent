import copy
import json
import re
import time
from enum import Enum
from typing import Any, Dict, List, Literal, Optional, cast

from pydantic import BaseModel

from workflow.cache.event_registry import EventRegistry
from workflow.engine.callbacks.callback_handler import ChatCallBacks
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseLLMNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.question_answer.prompt import system_prompt
from workflow.engine.nodes.util.prompt import prompt_template_replace
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span
from workflow.utils.json_schema.json_schema_validator import JsonSchemaValidator


class EventType(str, Enum):
    """
    Three event types for question-answer node: resume, ignore, abort
    """

    EVENT_RESUME = "resume"
    EVENT_IGNORE = "ignore"
    EVENT_ABORT = "abort"


class SystemOutputVariable(str, Enum):
    """
    System output variable names for question-answer node
    """

    ID = "id"
    QUERY = "query"
    CONTENT = "content"


class AnswerType(str, Enum):
    """
    Answer types for question-answer node
    """

    DIRECT = "direct"
    OPTION = "option"


class OptionType(int, Enum):
    """
    Option types for question-answer node
    """

    DEFAULT = 1
    USER = 2


class DirectAnswer(BaseModel):
    """
    Configuration for direct answer type

    :param handleResponse: Whether to handle response with LLM
    :param maxRetryCounts: Maximum number of retry attempts
    """

    handleResponse: bool = False
    maxRetryCounts: int = 0


class Option(BaseModel):
    """
    Option configuration for question-answer node

    :param id: Unique identifier for the option
    :param name: Display name for the option
    :param type: Option type (1 for default, 2 for user)
    :param content_type: Type of content (text, image, etc.)
    :param content: Content of the option
    """

    id: str
    name: str
    type: int
    content_type: str
    content: str


class ResumeData(BaseModel):
    """
    Resume data structure for question-answer node

    :param event_type: Type of event (resume, ignore, abort)
    :param content: Content of the resume data
    :param retries: Number of retry attempts
    :param timestamp: Timestamp of the event
    """

    event_type: str
    content: str
    retries: int
    timestamp: int


class InterruptOption(BaseModel):
    """
    Interrupt option structure

    :param id: Option identifier
    :param text: Option text content
    :param content_type: Type of content
    """

    id: str
    text: str
    content_type: str


class InterruptData(BaseModel):
    """
    Interrupt data structure for question-answer node

    :param type: Type of interrupt (option or direct)
    :param content: Content of the interrupt
    :param option: List of options (for option type)
    """

    type: str
    content: str
    option: list = []

    def to_dict(self) -> dict:
        """
        Convert to dictionary format

        :return: Dictionary representation of the interrupt data
        """
        if self.option:
            return {"type": self.type, "content": self.content, "option": self.option}
        else:
            return {"type": self.type, "content": self.content}


class PromptResult(BaseModel):
    """
    Result structure for prompt processing

    :param role: Role of the response (default: assistant)
    :param content: Content of the response
    :param complete_data: Successfully extracted data
    :param incomplete_data: Incomplete or missing data
    """

    role: str = "assistant"
    content: str = ""
    complete_data: dict
    incomplete_data: dict


class QuestionAnswerNode(BaseLLMNode):
    """
    Question-Answer node implementation for interactive workflows

    This node handles both option-based and direct answer types,
    supporting user interaction through interrupts and resume mechanisms.
    """

    question: str
    answerType: Literal["option", "direct"]
    timeout: int
    needReply: bool
    directAnswer: DirectAnswer
    optionAnswer: list[Option] = []
    start_time: float = 0.0
    event_id: str = ""
    extractor_params: list = []
    default_outputs: dict = {}
    instruction: str = ""
    token_usage: dict = {}
    processed_options: List[Option] = []

    def get_node_config(self) -> Dict[str, Any]:
        """
        Get node configuration as dictionary

        :return: Dictionary containing all node configuration parameters
        """
        return {
            "question": self.question,
            "answerType": self.answerType,
            "timeout": self.timeout,
            "needReply": self.needReply,
            "directAnswer": self.directAnswer.dict(),
            "optionAnswer": (
                [opt.dict() for opt in self.optionAnswer] if self.optionAnswer else []
            ),
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
            "start_time": self.start_time,
            "event_id": self.event_id,
            "default_output": self.default_outputs,
        }

    def sync_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Synchronous execution method (not implemented)

        :param variable_pool: Variable pool for data access
        :param span: Tracing span for monitoring
        :param event_log_node_trace: Event logging trace
        :param kwargs: Additional keyword arguments
        :return: Node execution result
        :raises NotImplementedError: This method is not implemented
        """
        raise NotImplementedError

    def assemble_schema_info(self) -> dict:
        """
        Assemble schema information from extractor parameters

        This function collects schema information from extractor parameters
        and returns a dictionary containing this information.

        :return: Dictionary containing schema information
        """
        schema_content: dict = {"type": "object", "properties": {}, "required": []}
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

    def schema_fixed_data(self, res_dict: dict, variable_pool: VariablePool) -> dict:
        """
        Fix input data structure to conform to JSON Schema based on templates and output variable mappings

        :param res_dict: Input data dictionary that needs to be fixed
        :param variable_pool: Variable pool object containing validation templates and output variable mappings
        :return: Fixed data dictionary
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
        # Validate and fix data
        is_valid, fixed_data = validator.validate_and_fix(res_dict)
        return fixed_data

    def calculate_usage_token(self, token_usage: dict) -> None:
        """
        Calculate and update token usage statistics

        :param token_usage: Dictionary containing token usage information to update
        """
        for key, value in token_usage.items():
            if key in self.token_usage:
                self.token_usage[key] += value
            else:
                self.token_usage[key] = value

    def process_option_answers(
        self, span_context: Span, variable_pool: VariablePool
    ) -> list:
        """
        Process option answer data

        :param span_context: Span object for tracing
        :param variable_pool: Variable pool for template variable replacement
        :return: Processed option list in format [{"id": str, "text": str}, ...]
        """
        interrupt_options: list = []
        if not self.optionAnswer:
            return interrupt_options

        default_option_cnt = 0

        for option in self.optionAnswer:
            replaced_content = prompt_template_replace(
                input_identifier=self.input_identifier,
                _prompt_template=option.content,
                node_id=self.node_id,
                variable_pool=variable_pool,
                span_context=span_context,
            )
            processed_option = Option(
                id=option.id,
                name=option.name,
                type=option.type,
                content=replaced_content,
                content_type=option.content_type,
            )
            if processed_option.type == OptionType.DEFAULT.value:
                default_option_cnt += 1
            self.processed_options.append(processed_option)

            interrupt_option = InterruptOption(
                id=option.name, content_type=option.content_type, text=replaced_content
            )
            interrupt_options.append(interrupt_option.dict())

        if default_option_cnt > 1:
            err_msg = "Invalid default option branch configuration"
            raise CustomException(
                err_code=CodeEnum.EngProtocolValidateErr,
                err_msg=err_msg,
                cause_error="Invalid default option branch configuration",
            )

        span_context.add_info_events(
            {"interrupt option": json.dumps(interrupt_options, ensure_ascii=False)}
        )
        return interrupt_options

    async def qa_fetch_resume_data(self, span_context: Span) -> ResumeData:
        """
        Asynchronously fetch resume data

        :param span_context: Context object for tracking and recording events
        :return: ResumeData object containing event type, content, retries, and timestamp
        :raises CustomException: When specific errors occur
        """
        try:
            event = EventRegistry().get_event(event_id=self.event_id)
            if event is None:
                raise CustomException(
                    err_code=CodeEnum.EventRegistryNotFoundError,
                    err_msg=CodeEnum.EventRegistryNotFoundError.msg,
                    cause_error="Event does not exist",
                )
            res = await EventRegistry().fetch_resume_data(
                queue_name=event.get_node_q_name(), timeout=self.timeout
            )
            if res:
                msg_str = res.get("message", "")
                message: Dict[str, Any] = json.loads(msg_str)
                metadata = res.get("metadata", {})
                resume_data = ResumeData(
                    event_type=message.get("event_type", ""),
                    content=message.get("content", ""),
                    retries=int(metadata.get("retries", "0")),
                    timestamp=int(metadata.get("timestamp", "0")),
                )
                span_context.add_info_events(
                    {"resume_data": json.dumps(res, ensure_ascii=False)}
                )
                return resume_data
            else:
                err_msg = "Resume data exception"
                span_context.add_error_event(err_msg)
                raise CustomException(
                    err_code=CodeEnum.QuestionAnswerResumeDataError,
                    err_msg=err_msg,
                    cause_error=err_msg,
                )

        except CustomException as err:
            raise err

        except Exception as e:
            raise CustomException(
                err_code=CodeEnum.QuestionAnswerResumeDataError,
                err_msg=str(e),
                cause_error=str(e),
            ) from e

    async def send_interrupt_callback(
        self, callbacks: ChatCallBacks, data: InterruptData
    ) -> None:
        """
        Send interrupt callback function

        This function is used to call specified callback functions when the node is interrupted
        and pass related data.

        :param callbacks: Object containing callback functions
        :param data: Interrupt data object containing interrupt-related information
        """
        await callbacks.on_node_interrupt(
            event_id=self.event_id,
            value=data.to_dict(),
            need_reply=self.needReply,
            code=0,
            node_id=self.node_id,
            alias_name=self.alias_name,
            finish_reason="interrupt",
        )

    async def handle_static_option_response(
        self,
        span_context: Span,
        inputs: dict,
        outputs: dict,
        resume_data: ResumeData,
        variable_pool: VariablePool,
    ) -> NodeRunResult:
        """
        Handle static option response asynchronously

        :param span_context: Tracing context for recording events and errors
        :param inputs: Input data dictionary
        :param outputs: Output data dictionary
        :param resume_data: Resume data object containing user reply content
        :param variable_pool: Variable pool for getting variable values
        :return: NodeRunResult object containing node execution result and related information
        """
        user_reply = resume_data.content  # User reply content, e.g., "A"
        span_context.add_info_events({"option_reply_content": user_reply})

        option_output: Option | None = None
        default_option: Option | None = None
        branch_id: str = ""
        # Single traversal to find matching item and default item
        for option in self.processed_options:
            # Standardized option name comparison
            if option.name == user_reply:
                option_output = option
                break
            if option.type == OptionType.DEFAULT.value:
                default_option = option

        if not option_output:
            if not default_option:
                raise CustomException(
                    err_code=CodeEnum.QuestionAnswerNodeExecutionError,
                    err_msg="No matching option and no default option",
                )
            option_output = default_option

            # Prioritize matching item, then use default item
            branch_id = option_output.id

        # If no valid branch, record error
        if not branch_id:
            err_msg = (
                f"No matching option and no default option, user input: {user_reply}"
            )
            span_context.add_error_event(err_msg)
            raise CustomException(
                err_code=CodeEnum.EngProtocolValidateErr,
                err_msg=err_msg,
                cause_error=err_msg,
            )

        if option_output:
            outputs.update(option_output.dict())

        # The ID to output for option answer is actually the option's name
        outputs[SystemOutputVariable.ID.value] = outputs["name"]

        span_context.add_info_events(
            {"static_option_response": json.dumps(outputs, ensure_ascii=False)}
        )

        order_outputs = {}
        for output in self.output_identifier:
            if output in outputs:
                order_outputs.update({output: outputs.get(output)})
            else:
                order_outputs.update(
                    {
                        output: variable_pool.get_variable(
                            node_id=self.node_id, key_name=output, span=span_context
                        )
                    }
                )
        return self._build_node_result(
            status=WorkflowNodeExecutionStatus.SUCCEEDED,
            inputs=inputs,
            outputs=order_outputs,
            branch_id=branch_id,
        )

    async def handle_dynamic_option_response(self) -> None:
        """
        Handle dynamic option response logic

        This method is currently not implemented.
        """
        pass

    async def handle_direct_response(
        self,
        span_context: Span,
        inputs: dict,
        resume_data: ResumeData,
        variable_pool: VariablePool,
    ) -> NodeRunResult:
        """
        Handle direct response asynchronously

        :param span_context: Tracing context object for recording and passing distributed tracing information
        :param inputs: Input parameters dictionary
        :param resume_data: Resume data object
        :param variable_pool: Variable pool object for getting variable values
        :return: NodeRunResult object containing node execution result and related information
        """
        # Dynamically construct output dictionary for easy extension
        final_res = {"query": self.question, "content": resume_data.content}
        span_context.add_info_events(
            {"direct_response": json.dumps(final_res, ensure_ascii=False)}
        )

        order_outputs = {}
        for output in self.output_identifier:
            if output in final_res:
                order_outputs.update({output: final_res.get(output)})
            else:
                order_outputs.update(
                    {
                        output: variable_pool.get_variable(
                            node_id=self.node_id, key_name=output, span=span_context
                        )
                    }
                )
        return self._build_node_result(
            status=WorkflowNodeExecutionStatus.SUCCEEDED,
            inputs=inputs,
            outputs=order_outputs,
        )

    async def async_execute_prompt(
        self,
        user_input: str,
        callbacks: ChatCallBacks,
        history: list,
        span_context: Span,
        variable_pool: VariablePool,
        event_log_node_trace: Optional[NodeLog] = None,
    ) -> PromptResult:
        """
        Asynchronously execute prompt processing, call Spark AI model and parse returned results

        :param user_input: User input text
        :param callbacks: Chat callbacks object
        :param history: Context history messages
        :param span_context: Span object for tracing
        :param variable_pool: Variable pool for data access
        :param event_log_node_trace: Event logging trace object
        :return: PromptResult: Standard structured result returned by the model
        :raises CustomException: When parsing fails or call errors occur
        """
        try:
            # 1. Assemble schema information
            schema_content = self.assemble_schema_info()
            span_context.add_info_events(
                {"cs_schema": json.dumps(schema_content), "user_input": user_input}
            )
            prompt_result = None
            user_prompt = (
                system_prompt.replace(
                    "{{histories}}", json.dumps(history, ensure_ascii=False)
                )
                .replace("{{instruction}}", self.instruction)
                .replace(
                    "{{json_structure}}", json.dumps(schema_content, ensure_ascii=False)
                )
                .replace("{{user_text}}", user_input)
            )
            span_context.add_info_events({"user_prompt": user_prompt})

            # 4. Call LLM service
            token_usage, response, _, _ = await self._chat_with_llm(
                flow_id=callbacks.flow_id,
                span=span_context,
                variable_pool=variable_pool,
                prompt_template=user_prompt,
                event_log_node_trace=event_log_node_trace,
            )
            self.calculate_usage_token(token_usage)
            span_context.add_info_events(
                {"token_usage": json.dumps(self.token_usage, ensure_ascii=False)}
            )
            # 5. Extract JSON block
            json_match = re.search(r"```json\s*\n?(.*?)\n?```", response, re.DOTALL)
            json_str = json_match.group(1).strip() if json_match else response.strip()
            span_context.add_info_events({"llm_result": response, "json_str": json_str})

            # 6. Safely parse JSON
            try:
                model_res = json.loads(json_str)
                if not isinstance(model_res, dict):
                    err_msg = f"Parse result: {model_res} is abnormal"
                    raise CustomException(
                        err_code=CodeEnum.QuestionAnswerHandlerResponseError,
                        err_msg=err_msg,
                        cause_error=err_msg,
                    )

                # Record parsed content
                span_context.add_info_events(
                    {
                        "extracted_params": json.dumps(model_res, ensure_ascii=False),
                        "token_usage": str(token_usage),
                    }
                )

                # 7. Build return object
                prompt_result = PromptResult(
                    role=model_res.get("role", "assistant"),
                    content=model_res.get("content", ""),
                    complete_data=model_res.get("completed", {}),
                    incomplete_data=model_res.get("incomplete", {}),
                )
                return prompt_result

            except (json.JSONDecodeError, ValueError) as e:
                err_msg_log = (
                    f"JSON parsing failed: {str(e)}, original response: {response}"
                )
                span_context.add_error_event(err_msg_log)
                raise CustomException(
                    err_code=CodeEnum.QuestionAnswerHandlerResponseError,
                    err_msg=err_msg_log,
                    cause_error=err_msg_log,
                )

        except CustomException as err:
            raise err

        except Exception as e:
            err_msg = f"Error executing prompt processing: {str(e)}"
            span_context.add_error_event(err_msg)
            raise CustomException(
                err_code=CodeEnum.QuestionAnswerHandlerResponseError,
                err_msg=err_msg,
                cause_error=err_msg,
            )

    async def handle_prompt_template_response(
        self,
        span_context: Span,
        inputs: dict,
        outputs: dict,
        callbacks: ChatCallBacks,
        resume_data: ResumeData,
        variable_pool: VariablePool,
        history: list = [],
        current_retries: int = 1,  # Add recursive depth counter
        event_log_node_trace: NodeLog | None = None,
    ) -> NodeRunResult:
        """
        Recursively handle templated direct answer logic with variable extraction,
        supporting interrupt waiting for ResumeData retry

        :param span_context: Tracing context
        :param inputs: Input data dictionary
        :param outputs: Output data dictionary
        :param callbacks: Chat callbacks object
        :param resume_data: Resume data object
        :param variable_pool: Variable pool for data access
        :param history: Conversation history
        :param current_retries: Current retry count
        :param event_log_node_trace: Event logging trace
        :return: Node execution result
        """

        max_retries = self.directAnswer.maxRetryCounts
        if not history:
            history = [
                {"role": "assistant", "content": self.question},
                {"role": "user", "content": resume_data.content},
            ]

        # Execute prompt slot extraction
        prompt_result = await self.async_execute_prompt(
            user_input=resume_data.content,
            callbacks=callbacks,
            history=history,
            span_context=span_context,
            variable_pool=variable_pool,
            event_log_node_trace=event_log_node_trace,
        )
        span_context.add_info_events(
            {
                "current_retries": current_retries,
                "prompt_result": json.dumps(prompt_result.dict(), ensure_ascii=False),
            }
        )

        # Slot extraction successful, return directly
        if not prompt_result.incomplete_data:
            # Output uses the latest user input content
            user_histories = [item for item in history if item["role"] == "user"]
            content = user_histories[-1].get("content", "")

            outputs.update({SystemOutputVariable.CONTENT.value: content})

            default_outputs = self.default_outputs.copy()

            outputs.update(prompt_result.complete_data)
            # Use default values for non-required slot extraction
            for k, v in default_outputs.items():
                outputs.setdefault(k, v)

            # Process output data
            res_dict = {
                output: outputs.get(
                    output,
                    variable_pool.get_variable(
                        node_id=self.node_id, key_name=output, span=span_context
                    ),
                )
                for output in self.output_identifier
            }

            res_dict = self.schema_fixed_data(res_dict, variable_pool)
            span_context.add_info_events(
                {"handle_prompt_result": json.dumps(res_dict, ensure_ascii=False)}
            )

            # Return result
            res = self._build_node_result(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=inputs,
                outputs=res_dict,
                raw_outputs=json.dumps(outputs, ensure_ascii=False),
            )
            return res

        # Exceeded maximum retry count, throw error
        if current_retries == max_retries:
            err_msg = "Parameter extraction failed, reached maximum retry count limit"
            span_context.add_error_event(err_msg)
            raise CustomException(
                err_code=CodeEnum.QuestionAnswerHandlerResponseError,
                err_msg=err_msg,
                cause_error=err_msg,
            )

        # Slot extraction incomplete, send interrupt waiting for ResumeData
        value = InterruptData(
            type=AnswerType.DIRECT.value, content=prompt_result.content
        )
        await self.send_interrupt_callback(callbacks=callbacks, data=value)
        span_context.add_info_events(
            {"retry_interrupt_data": json.dumps(value.dict(), ensure_ascii=False)}
        )

        # Wait for user Resume reply
        resume_data = await self.qa_fetch_resume_data(span_context=span_context)
        current_retries = resume_data.retries
        span_context.add_info_events(
            {"retry_resume_data": json.dumps(resume_data.dict(), ensure_ascii=False)}
        )

        # Concatenate conversation history
        history.append({"role": "assistant", "content": prompt_result.content})
        history.append({"role": "user", "content": resume_data.content})
        span_context.add_info_events(
            {"retry_history": json.dumps(history, ensure_ascii=False)}
        )
        if resume_data.event_type == EventType.EVENT_RESUME.value:
            # Recursive call processing
            return await self.handle_prompt_template_response(
                span_context=span_context,
                inputs=inputs,
                outputs=outputs,
                callbacks=callbacks,
                resume_data=resume_data,
                event_log_node_trace=event_log_node_trace,
                variable_pool=variable_pool,
                history=history,
                current_retries=current_retries + 1,
            )
        else:
            return await self.handle_ignore_abort_event(
                resume_data=resume_data,
                span_context=span_context,
                inputs=inputs,
                outputs=outputs,
            )

    async def handle_ignore_abort_event(
        self, resume_data: ResumeData, span_context: Span, inputs: dict, outputs: dict
    ) -> NodeRunResult:
        """
        Handle ignore and abort events

        :param resume_data: Resume data object
        :param span_context: Tracing context
        :param inputs: Input data dictionary
        :param outputs: Output data dictionary
        :return: Node execution result
        """
        event_type = resume_data.event_type
        if event_type == EventType.EVENT_ABORT.value:
            await self._handle_abort_event(span_context)

        if event_type == EventType.EVENT_IGNORE.value:
            return await self._handle_ignore_event(
                span_context, resume_data, inputs, outputs
            )

        err_msg = f"Abnormal event type: {event_type}"
        raise CustomException(
            err_code=CodeEnum.QuestionAnswerResumeDataError,
            err_msg=err_msg,
            cause_error=err_msg,
        )

    async def _handle_abort_event(self, span_context: Span) -> None:
        """
        Handle abort event

        :param span_context: Tracing context
        :raises CustomException: When abort event is received
        """
        err_msg = "Received abort instruction"
        span_context.add_info_event(err_msg)
        raise CustomException(
            err_code=CodeEnum.QuestionAnswerResumeDataError,
            err_msg=err_msg,
            cause_error=err_msg,
        )

    async def _handle_ignore_event(
        self, span_context: Span, resume_data: ResumeData, inputs: dict, outputs: dict
    ) -> NodeRunResult:
        """
        Handle ignore event

        :param span_context: Tracing context
        :param resume_data: Resume data object
        :param inputs: Input data dictionary
        :param outputs: Output data dictionary
        :return: Node execution result
        """
        if self.needReply:
            err_msg = (
                "Received ignore instruction, but workflow does not support ignore"
            )
            raise CustomException(
                err_code=CodeEnum.QuestionAnswerResumeDataError,
                err_msg=err_msg,
                cause_error=err_msg,
            )

        span_context.add_info_event("Received ignore instruction")

        default_outputs = (
            self.default_outputs.copy()
        )  # Prevent original data from being polluted

        outputs.update({SystemOutputVariable.CONTENT.value: resume_data.content})

        outputs.update(default_outputs)
        branch_id = ""
        if self.answerType == AnswerType.OPTION.value:
            branch_id = self._get_first_option_id_by_type(OptionType.DEFAULT.value)
            outputs.update({"id": "default"})
            span_context.add_info_events(
                {"ignore_option": json.dumps(outputs, ensure_ascii=False)}
            )
        else:
            span_context.add_info_events(
                {"ignore_direct": json.dumps(outputs, ensure_ascii=False)}
            )

        return self._build_node_result(
            status=WorkflowNodeExecutionStatus.SUCCEEDED,
            inputs=inputs,
            outputs=outputs,
            branch_id=branch_id,
        )

    def _get_first_option_id_by_type(self, target_type: int) -> str:
        """
        Get the first option ID by type

        :param target_type: Target option type
        :return: Option ID if found, empty string otherwise
        """
        for option in self.optionAnswer:
            if option.type == target_type:
                return option.id
        return ""

    def _build_node_result(
        self,
        status: WorkflowNodeExecutionStatus,
        inputs: dict,
        outputs: dict,
        raw_outputs: str = "",
        branch_id: str = "",
        error: Optional[CustomException] = None,
    ) -> NodeRunResult:
        """
        Build node execution result

        :param status: Node execution status
        :param inputs: Input data dictionary
        :param outputs: Output data dictionary
        :param raw_outputs: Raw output string
        :param branch_id: Branch ID for conditional execution
        :param error: Error message
        :param error_code: Error code
        :return: Node execution result
        """
        res = NodeRunResult(
            node_id=self.node_id,
            alias_name=self.alias_name,
            node_type=self.node_type,
            status=status,
            inputs=inputs,
            error=error,
            outputs=outputs,
            raw_output=raw_outputs if raw_outputs else "",
            edge_source_handle=branch_id if branch_id else None,
        )
        return res

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronously execute the question-answer node

        :param variable_pool: Variable pool for data access
        :param span: Tracing span for monitoring
        :param event_log_node_trace: Event logging trace
        :param kwargs: Additional keyword arguments
        :return: Node execution result
        """

        callbacks = cast(ChatCallBacks, kwargs.get("callbacks"))
        self.start_time = time.time()
        self.timeout = self.timeout * 60  # Convert timeout from minutes to seconds
        try:
            # Process question content
            self.question = prompt_template_replace(
                input_identifier=self.input_identifier,
                _prompt_template=self.question,
                node_id=self.node_id,
                variable_pool=variable_pool,
                span_context=span,
            )

            # Process input
            inputs = {}
            for input_key in self.input_identifier:
                val = variable_pool.get_variable(
                    node_id=self.node_id, key_name=input_key, span=span
                )
                inputs[input_key] = val

            # Process output query
            outputs = {}
            outputs.update({SystemOutputVariable.QUERY.value: self.question})

            span.add_info_events(
                {
                    "question": self.question,
                    "inputs": json.dumps(inputs, ensure_ascii=False),
                    "output": json.dumps(outputs, ensure_ascii=False),
                }
            )

            self.event_id = callbacks.event_id

            # Register node interrupt event
            EventRegistry().on_interrupt_node_start(
                event_id=self.event_id, node_id=self.node_id, timeout=self.timeout
            )
            span.add_info_events(
                {
                    "interrupt_info": f"event_id: {self.event_id}, node_id: {self.node_id}, timeout: {str(self.timeout)}"
                }
            )

            await callbacks.on_node_start(
                code=0, node_id=self.node_id, alias_name=self.alias_name
            )

            # Construct different interrupt data based on answerType
            if self.answerType == AnswerType.OPTION.value:
                value = InterruptData(
                    type=AnswerType.OPTION.value,
                    content=self.question,
                    option=self.process_option_answers(
                        span_context=span, variable_pool=variable_pool
                    ),
                )
            else:
                value = InterruptData(
                    type=AnswerType.DIRECT.value, content=self.question
                )

            span.add_info_events(
                {"interrupt_data": json.dumps(value.dict(), ensure_ascii=False)}
            )

            await self.send_interrupt_callback(callbacks=callbacks, data=value)
            resume_data = await self.qa_fetch_resume_data(span_context=span)
            action_type = resume_data.event_type
            node_res = None

            # Handle resume logic
            if action_type == EventType.EVENT_RESUME.value:
                if self.answerType == AnswerType.OPTION.value:
                    node_res = await self.handle_static_option_response(
                        span_context=span,
                        inputs=inputs,
                        outputs=outputs,
                        resume_data=resume_data,
                        variable_pool=variable_pool,
                    )

                elif self.answerType == "direct":
                    if self.directAnswer.handleResponse:
                        node_res = await self.handle_prompt_template_response(
                            span_context=span,
                            inputs=inputs,
                            outputs=outputs,
                            resume_data=resume_data,
                            variable_pool=variable_pool,
                            callbacks=callbacks,
                            event_log_node_trace=event_log_node_trace,
                        )
                    else:
                        node_res = await self.handle_direct_response(
                            span_context=span,
                            inputs=inputs,
                            resume_data=resume_data,
                            variable_pool=variable_pool,
                        )

            # Handle ignore logic
            else:
                node_res = await self.handle_ignore_abort_event(
                    resume_data=resume_data,
                    span_context=span,
                    inputs=inputs,
                    outputs=outputs,
                )
            EventRegistry().on_interrupt_node_end(event_id=self.event_id)
            return node_res

        except CustomException as e:
            EventRegistry().on_interrupt_node_end(event_id=self.event_id)
            return self._build_node_result(
                status=WorkflowNodeExecutionStatus.FAILED,
                error=e,
                inputs={},
                outputs={},
            )
        except Exception as e:
            EventRegistry().on_interrupt_node_end(event_id=self.event_id)
            return self._build_node_result(
                status=WorkflowNodeExecutionStatus.FAILED,
                error=CustomException(
                    err_code=CodeEnum.QuestionAnswerNodeExecutionError, cause_error=e
                ),
                inputs={},
                outputs={},
            )
