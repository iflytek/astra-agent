# Standard library imports
import json
from enum import Enum
from typing import Any, Dict, List, Literal, Union

from pydantic import Field

# Workflow engine imports
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.util.prompt import prompt_template_replace

# Exception handling imports
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum

# Logging and tracing imports
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span


class TextProcessModeEnum(Enum):
    """
    Enumeration for text processing modes in TextJoinerNode.

    Defines the available modes for text processing operations:
    - JOIN_MODE: Concatenates multiple text inputs using a template
    - SEPARATE_MODE: Splits a single text input using a separator
    """

    JOIN_MODE = 0  # Text concatenation mode
    SEPARATE_MODE = 1  # Text separation mode


class TextJoinerNode(BaseNode):
    """
    A workflow node for text processing operations.

    This node supports two main text processing modes:
    1. JOIN_MODE: Combines multiple text inputs using a template-based approach
    2. SEPARATE_MODE: Splits a single text input into multiple parts using a separator

    The node can handle various text manipulation tasks within workflow pipelines,
    providing flexible text processing capabilities for different use cases.

    Attributes:
        mode: Processing mode (0 for JOIN_MODE, 1 for SEPARATE_MODE)
        prompt: Template string used for text concatenation in JOIN_MODE
        separator: Delimiter used for text splitting in SEPARATE_MODE
    """

    mode: Literal[0, 1] = Field(default=0)  # Text processing mode (0=JOIN, 1=SEPARATE)
    prompt: str = Field(default="")  # Template for text concatenation
    separator: str = Field(default="")  # Delimiter for text separation

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronously execute text processing operations.

        Performs text processing based on the configured mode:
        - JOIN_MODE: Combines multiple inputs using a template
        - SEPARATE_MODE: Splits a single input using a separator

        :param variable_pool: Pool containing workflow variables and inputs
        :param span: Tracing span for monitoring execution performance
        :param event_log_node_trace: Optional node logging trace for debugging
        :param kwargs: Additional keyword arguments (unused)
        :return: NodeRunResult containing execution status and processed text
        :raises CustomException: For workflow-specific errors
        :raises Exception: For general execution errors
        """
        try:
            inputs: Dict[str, Any] = {}
            final_res: Union[str, List[str]] = ""

            if self.mode == TextProcessModeEnum.JOIN_MODE.value:
                # Text concatenation mode - combine multiple inputs using template
                for input_key in self.input_identifier:
                    val = variable_pool.get_variable(
                        node_id=self.node_id, key_name=input_key, span=span
                    )
                    inputs[input_key] = val

                final_res = prompt_template_replace(
                    input_identifier=self.input_identifier,
                    _prompt_template=self.prompt,
                    node_id=self.node_id,
                    variable_pool=variable_pool,
                    span_context=span,
                )
            elif self.mode == TextProcessModeEnum.SEPARATE_MODE.value:
                # Text separation mode - split single input using separator
                inputs = {}
                org_text = ""
                for input_key in self.input_identifier:
                    val = variable_pool.get_variable(
                        node_id=self.node_id, key_name=input_key, span=span
                    )
                    inputs[input_key] = val
                    org_text = val
                final_res = org_text.split(self.separator)

            # Return successful execution result with processed text
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=inputs,
                raw_output=(
                    final_res
                    if isinstance(final_res, str)
                    else json.dumps(final_res, ensure_ascii=False)
                ),
                outputs={self.output_identifier[0]: final_res},
            )
        except CustomException as err:
            # Handle workflow-specific custom exceptions
            span.record_exception(err)
            run_result = NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=err,
            )
            return run_result
        except Exception as err:
            # Handle general exceptions with generic error code
            span.record_exception(err)
            return NodeRunResult(
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
                status=WorkflowNodeExecutionStatus.FAILED,
                error=CustomException(
                    CodeEnum.TEXT_JOINER_NODE_EXECUTION_ERROR,
                    cause_error=err,
                ),
            )
