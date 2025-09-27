import json
import os
import re
from typing import Any, Dict, Literal

from pydantic import Field

from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.code.executor.base_executor import CodeExecutorFactory
from workflow.engine.nodes.entities.node_run_result import NodeRunResult
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span

# Python code execution template that wraps user code with main function call
PYTHON_RUNNER = """{{code}}

output = None
# Execute main function with provided inputs and return the result
# inputs is a dictionary containing parameter values
output = main(**{{inputs}})

# Convert output to JSON string and print for result capture
if output is not None:
    if type(output) != str:
        import json
        output = json.dumps(output, ensure_ascii=False)

    result = f'''{output}'''

    print(result)
"""


class CodeNode(BaseNode):
    """
    Code execution node that allows running Python code within workflow.

    This node provides a secure environment for executing user-defined Python code,
    supporting parameter injection and result extraction with type validation.
    """

    codeLanguage: Literal["python"] = Field("python", description="Code language")
    code: str = Field(..., description="Code")
    appId: str = Field(..., description="App ID")
    uid: str = Field(..., description="User ID")

    def _get_actual_parameter(
        self, variable_pool: VariablePool, span_context: Span
    ) -> Dict[str, Any]:
        """
        Extract actual parameter values from variable pool based on code function signature.

        :param variable_pool: Pool containing workflow variables
        :param span_context: Tracing span for logging
        :return: Dictionary of parameter names and their values
        """
        func_variables = _parser_code_parameter(self.code)
        actual_parameters = {}
        for variable_key in func_variables:
            value_content = variable_pool.get_variable(
                node_id=self.node_id, key_name=variable_key, span=span_context
            )
            actual_parameters.update({variable_key: value_content})
        span_context.add_info_events(
            {"input": json.dumps(actual_parameters, ensure_ascii=False)}
        )
        return actual_parameters

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronously execute the code node.

        :param variable_pool: Pool containing workflow variables
        :param span: Tracing span for logging
        :param event_log_node_trace: Optional node trace logger
        :param kwargs: Additional keyword arguments
        :return: Node execution result with outputs and timing information
        """
        try:
            actual_parameters = self._get_actual_parameter(
                variable_pool=variable_pool, span_context=span
            )

            code_result = await self.execute_code(
                parameters=actual_parameters,
                span_context=span,
            )

            outputs = self._check_and_set_variable_pool(
                variable_pool, code_result, span
            )

            return self.success(
                inputs=actual_parameters,
                outputs=outputs,
                raw_output=(
                    code_result if isinstance(code_result, str) else str(code_result)
                ),
            )
        except Exception as err:
            return self.fail(err, CodeEnum.CODE_EXECUTION_ERROR, span)

    async def execute_code(self, parameters: dict, span_context: Span) -> dict:
        """
        Execute the user-defined code with provided parameters.

        :param parameters: Dictionary of parameter values to pass to the code
        :param span_context: Tracing span for logging execution details
        :return: Dictionary containing the execution result
        """
        # Convert parameters to string format for code injection
        actual_parameters_str = str(parameters)
        # Replace placeholders in the Python runner template
        runner = PYTHON_RUNNER.replace("{{code}}", self.code)
        runner = runner.replace("{{inputs}}", actual_parameters_str)

        span_context.add_info_event(f"runner code: {runner}")

        # Create appropriate code executor based on environment configuration
        code_executor = CodeExecutorFactory.create_executor(
            os.getenv("CODE_EXEC_TYPE", "local")
        )
        # Execute code with timeout configuration
        result_str = await code_executor.execute(
            language="python",
            code=runner,
            timeout=int(
                self.retry_config.timeout
                if self.retry_config.timeout
                else int(os.getenv("CODE_EXEC_TIMEOUT_SEC", "10"))
            ),
            span=span_context,
            app_id=self.appId,
            uid=self.uid,
        )

        # If the result is not a valid JSON string, return the result as a string
        try:
            return json.loads(result_str)
        except Exception as e:
            span_context.record_exception(e)
            return {
                self.output_identifier[0]: result_str,
            }

    def _check_and_set_variable_pool(
        self, variable_pool: VariablePool, code_result_dict: dict, span: Span
    ) -> dict:
        """
        Validate and set output variables in the variable pool with type checking.

        :param variable_pool: Pool containing workflow variables
        :param code_result_dict: Dictionary containing code execution results
        :param span: Tracing span for logging
        :return: Dictionary of validated output variables
        """
        outputs = {}
        for var_name in self.output_identifier:
            var_type = variable_pool.get_output_schema(
                node_id=self.node_id, key_name=var_name
            ).get("type")

            # If variable not in result, use existing value from variable pool
            if var_name not in code_result_dict:
                final_result = variable_pool.get_variable(
                    node_id=self.node_id, key_name=var_name, span=span
                )
                outputs.update({var_name: final_result})
                continue

            # Type validation based on expected output schema
            match var_type:
                case "string":
                    if isinstance(code_result_dict[var_name], str) is False:
                        raise CustomException(
                            CodeEnum.CODE_NODE_RESULT_TYPE_ERROR,
                            "Code return type is not str, please check the type!",
                        )
                case "integer":
                    if isinstance(code_result_dict[var_name], int) is False:
                        raise CustomException(
                            CodeEnum.CODE_NODE_RESULT_TYPE_ERROR,
                            "Code return type is not integer, please check the type!",
                        )

                case "number":
                    if isinstance(code_result_dict[var_name], (int, float)) is False:
                        raise CustomException(
                            CodeEnum.CODE_NODE_RESULT_TYPE_ERROR,
                            "Code return type is not integer or float, please check the type!",
                        )

                case "boolean":
                    if isinstance(code_result_dict[var_name], bool) is False:
                        raise CustomException(
                            CodeEnum.CODE_NODE_RESULT_TYPE_ERROR,
                            "Code return type is not bool, please check the type!",
                        )

                case "array":
                    if isinstance(code_result_dict[var_name], list) is False:
                        raise CustomException(
                            CodeEnum.CODE_NODE_RESULT_TYPE_ERROR,
                            "Code return type is not array, please check the type!",
                        )

                case "object":
                    if isinstance(code_result_dict[var_name], dict) is False:
                        raise CustomException(
                            CodeEnum.CODE_NODE_RESULT_TYPE_ERROR,
                            "Code return type is not object, please check the type!",
                        )

            outputs.update({var_name: code_result_dict[var_name]})
        return outputs


def _parser_code_parameter(python_code: str) -> list[str]:
    """
    Parse function parameters from Python code using regex.

    Extracts parameter names from the main function definition in the provided code.

    :param python_code: Python code string containing function definitions
    :return: List of parameter names from the main function
    :raises CustomException: If main function is not found in the code
    """
    # Remove comment lines to avoid parsing issues
    python_code = "\n".join(
        line for line in python_code.splitlines() if not line.strip().startswith("#")
    )
    # Regex pattern to match function definitions with optional type hints
    re_pattern = r"def\s+(\w+)\s*\(([^)]*)\)\s*(?:->\s*[\w\[\],\s]*)?:"
    re_matches = re.findall(re_pattern, python_code, re.DOTALL)
    re_parameter = ""
    # Find the main function specifically
    for re_match in re_matches:
        if re_match[0].strip() == "main":
            re_parameter = re_match[1].strip()
            break
    if not re_parameter:
        raise CustomException(
            CodeEnum.CODE_BUILD_ERROR,
            err_msg="can not find main function",
            cause_error="can not find main function",
        )
    # Split parameters and extract parameter names (remove type hints)
    re_params = re_parameter.split(",")
    variables = []
    for re_param in re_params:
        re_param = re_param.strip()
        if re_param:
            # Remove type hints if present (everything after colon)
            re_param = re_param.split(":")[0].strip()
            variables.append(re_param)
    return variables
