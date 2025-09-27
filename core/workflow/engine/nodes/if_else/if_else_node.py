from typing import Any, List, Literal, Optional

from pydantic import BaseModel, Field

from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span

# Default branch level for fallback cases
DEFAULT_BRANCH_LEVEL = 999


class Condition(BaseModel):
    """
    Condition.
    :param left_var_index: Index of the left variable
    :param right_var_index: Index of the right variable
    :param compare_operator: Comparison operator
    """

    leftVarIndex: str | None = None
    rightVarIndex: str | None = None
    compareOperator: Literal[
        "contains",
        "not_contains",
        "empty",
        "not_empty",
        "is",
        "is_not",
        "start_with",
        "end_with",
        "eq",
        "ne",
        "gt",
        "ge",
        "lt",
        "le",
        "null",
        "not_null",
    ]


class IfElseNodeData(BaseModel):
    """
    If-Else node data.
    :param id: ID of the if-else node
    :param level: Level of the if-else node
    :param logical_operator: Logical operator of the if-else node
    :param conditions: Conditions of the if-else node
    """

    id: str = Field(pattern=r"^branch_one_of::[0-9a-zA-Z-]+")
    level: int = Field(ge=1)
    logicalOperator: Literal["and", "or"]
    conditions: List[Condition]


class IFElseNode(BaseNode):
    """
    If-Else conditional node implementation.

    This node evaluates multiple branches with different conditions and executes
    the first branch that meets its criteria. It supports various comparison
    operators for strings, numbers, and collections.
    """

    cases: List[IfElseNodeData] = Field(min_length=2)

    async def do_one_branch(
        self,
        variable_pool: VariablePool,
        span: Span,
        branch_data: IfElseNodeData,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Execute a single branch within the if-else node.

        This method evaluates all conditions in the branch and applies the
        logical operator to determine if the branch should be executed.

        :param variable_pool: Variable pool containing runtime variables
        :param span: Tracing span for monitoring execution
        :param branch_data: Branch configuration and conditions to evaluate
        :param kwargs: Additional parameters including callback methods
        :return: Node execution result with condition evaluation results
        """

        node_inputs: dict[str, list] = {"conditions": []}

        process_datas: dict[str, list] = {"condition_results": []}

        node_data = branch_data
        with span.start(
            func_name="do_one_branch", add_source_function_name=True
        ) as span_context:
            try:
                # Get the logical operator for combining conditions
                logical_operator = node_data.logicalOperator
                input_conditions = []
                for condition in node_data.conditions:
                    if not condition:
                        continue
                    left_var_index = condition.leftVarIndex
                    left_var_name = self.input_identifier[0][left_var_index]
                    right_var_index = condition.rightVarIndex
                    right_var_name = self.input_identifier[0].get(right_var_index, "")

                    # Retrieve actual value from variable pool
                    actual_value = variable_pool.get_variable(
                        node_id=self.node_id, key_name=left_var_name, span=span_context
                    )

                    # Retrieve expected value from variable pool if specified
                    expected_value = None
                    if right_var_name != "":
                        expected_value = variable_pool.get_variable(
                            node_id=self.node_id,
                            key_name=right_var_name,
                            span=span_context,
                        )

                    input_conditions.append(
                        {
                            "actual_value": actual_value,
                            "expected_value": expected_value,
                            "comparison_operator": condition.compareOperator,
                        }
                    )

                node_inputs["conditions"] = input_conditions

                # Evaluate each condition using the appropriate comparison operator
                for input_condition in input_conditions:
                    actual_value = input_condition["actual_value"]
                    expected_value = input_condition["expected_value"]
                    comparison_operator = input_condition["comparison_operator"]

                    # Apply the specified comparison operator
                    match comparison_operator:
                        case "contains":
                            compare_result = self._assert_contains(
                                actual_value, expected_value
                            )
                        case "not_contains":
                            compare_result = self._assert_not_contains(
                                actual_value, expected_value
                            )
                        case "start_with":
                            compare_result = self._assert_start_with(
                                actual_value, expected_value
                            )
                        case "end_with":
                            compare_result = self._assert_end_with(
                                actual_value, expected_value
                            )
                        case "is":
                            compare_result = self._assert_is(
                                actual_value, expected_value
                            )
                        case "is_not":
                            compare_result = self._assert_is_not(
                                actual_value, expected_value
                            )
                        case "empty":
                            compare_result = self._assert_empty(
                                actual_value, input_condition
                            )
                        case "not_empty":
                            compare_result = self._assert_not_empty(
                                actual_value, input_condition
                            )
                        case "eq":
                            compare_result = self._assert_equal(
                                actual_value, expected_value
                            )
                        case "ne":
                            compare_result = self._assert_not_equal(
                                actual_value, expected_value
                            )
                        case "gt":
                            compare_result = self._assert_greater_than(
                                actual_value, expected_value
                            )
                        case "lt":
                            compare_result = self._assert_less_than(
                                actual_value, expected_value
                            )
                        case "ge":
                            compare_result = self._assert_greater_than_or_equal(
                                actual_value, expected_value
                            )
                        case "le":
                            compare_result = self._assert_less_than_or_equal(
                                actual_value, expected_value
                            )
                        case "null":
                            compare_result = self._assert_null(actual_value)
                        case "not_null":
                            compare_result = self._assert_not_null(actual_value)
                        case _:
                            continue

                    process_datas["condition_results"].append(
                        {**input_condition, "result": compare_result}
                    )

            except Exception as err:
                span_context.add_error_event(
                    f"err: {err}, err_code: {CodeEnum.IF_ELSE_NODE_EXECUTION_ERROR.code}"
                )
                return NodeRunResult(
                    status=WorkflowNodeExecutionStatus.FAILED,
                    inputs=node_inputs,
                    process_data=process_datas,
                    error=CustomException(
                        CodeEnum.IF_ELSE_NODE_EXECUTION_ERROR,
                        cause_error=err,
                    ),
                    node_id=self.node_id,
                    alias_name=self.alias_name,
                    node_type=self.node_type,
                )

            # Apply logical operator to combine all condition results
            if logical_operator == "and":
                # All conditions must be true for AND operation
                compare_result = False not in [
                    condition["result"]
                    for condition in process_datas["condition_results"]
                ]
            else:
                # At least one condition must be true for OR operation
                compare_result = True in [
                    condition["result"]
                    for condition in process_datas["condition_results"]
                ]

            compare_result_dict = {"res": compare_result}

            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=node_inputs,
                process_data=process_datas,
                outputs=compare_result_dict,
                edge_source_handle=node_data.id,
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronously execute the if-else node with short-circuit evaluation.

        This method evaluates branches in priority order and executes the first
        branch that meets its conditions. It follows short-circuit principles
        where evaluation stops once a matching branch is found.

        :param variable_pool: Variable pool containing runtime variables
        :param span: Tracing span for monitoring execution
        :param event_log_node_trace: Optional node trace logging
        :param kwargs: Additional parameters including callback methods
        :return: Node execution result from the first matching branch
        """
        res: NodeRunResult
        inputs: dict[str, Any] = {}
        errors: dict[str, Any] = {}
        try:
            # Execute each branch block in priority order, following short-circuit principle
            for index, cur_branch in enumerate(self.cases):
                # If we reach the default branch, return its result directly
                if cur_branch.level == DEFAULT_BRANCH_LEVEL:
                    return NodeRunResult(
                        status=WorkflowNodeExecutionStatus.SUCCEEDED,
                        inputs=inputs,
                        process_data={},
                        outputs=errors,
                        edge_source_handle=cur_branch.id,
                        node_id=self.node_id,
                        alias_name=self.alias_name,
                        node_type=self.node_type,
                    )

                res = await self.do_one_branch(
                    variable_pool=variable_pool, span=span, branch_data=cur_branch
                )
                # If a branch condition fails, collect error info and try next branch
                if res.status == WorkflowNodeExecutionStatus.FAILED:
                    inputs.update({f"Branch {index + 1} inputs: ": res.inputs})
                    errors.update({f"Branch {index + 1} errors: ": res.error})
                    continue
                # If branch conditions are met, execute this branch and stop
                if res.outputs.get("res") is True:
                    inputs.update({f"Branch {index + 1} inputs: ": res.inputs})
                    errors.update(
                        {f"Branch {index + 1} errors: ": "no error, execute it."}
                    )
                    break
                else:
                    # Branch conditions not met, continue to next branch
                    inputs.update({f"Branch {index + 1} inputs: ": res.inputs})
                    errors.update(
                        {
                            f"Branch {index + 1} errors: ": "no error, but not execute it."
                        }
                    )
        except Exception as err:
            span.add_error_event(f"{err}")
            if res is None:
                # Return default result when no branch was executed
                return NodeRunResult(
                    status=WorkflowNodeExecutionStatus.SUCCEEDED,
                    inputs=inputs,
                    process_datas={},
                    outputs=(
                        {
                            "[warning]: ": "If-else node encountered unknown error, please check!"
                        }
                        if len(errors) == 0
                        else errors
                    ),
                    edge_source_handle=self.cases[-1].id,
                    node_id=self.node_id,
                    alias_name=self.alias_name,
                    node_type=self.node_type,
                )

        if res is None:
            # Return default result when no branch was executed
            res = NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=inputs,
                process_datas={},
                outputs=(
                    {
                        "[warning]: ": "If-else node encountered unknown error, please check!"
                    }
                    if len(errors) == 0
                    else errors
                ),
                edge_source_handle=self.cases[-1].id,
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )
        return res

    def _assert_contains(
        self, actual_value: Optional[str | list], expected_value: str
    ) -> bool:
        """
        Check if the actual value contains the expected value.

        :param actual_value: The value to check (string or list)
        :param expected_value: The value to search for
        :return: True if actual_value contains expected_value, False otherwise
        """
        if actual_value == "" and expected_value == "":
            return True
        if not actual_value:
            return False

        if not isinstance(actual_value, str | list):
            return False

        if expected_value not in actual_value:
            return False
        return True

    def _assert_not_contains(
        self, actual_value: Optional[str | list], expected_value: str
    ) -> bool:
        """
        Check if the actual value does not contain the expected value.

        :param actual_value: The value to check (string or list)
        :param expected_value: The value to search for
        :return: True if actual_value does not contain expected_value, False otherwise
        """
        if actual_value == "" and expected_value == "":
            return False
        if not actual_value:
            return True

        if not isinstance(actual_value, str | list):
            return False

        if expected_value in actual_value:
            return False
        return True

    def _assert_start_with(
        self, actual_value: Optional[str], expected_value: str
    ) -> bool:
        """
        Check if the actual string starts with the expected value.

        :param actual_value: The string to check
        :param expected_value: The prefix to match
        :return: True if actual_value starts with expected_value, False otherwise
        """
        if not actual_value:
            return False

        if not isinstance(actual_value, str):
            return False

        if not actual_value.startswith(expected_value):
            return False
        return True

    def _assert_end_with(
        self, actual_value: Optional[str], expected_value: str
    ) -> bool:
        """
        Check if the actual string ends with the expected value.

        :param actual_value: The string to check
        :param expected_value: The suffix to match
        :return: True if actual_value ends with expected_value, False otherwise
        """
        if not actual_value:
            return False

        if not isinstance(actual_value, str):
            return False

        if not actual_value.endswith(expected_value):
            return False
        return True

    def _assert_is(self, actual_value: Optional[str], expected_value: str) -> bool:
        """
        Check if the actual value equals the expected value.

        :param actual_value: The value to compare
        :param expected_value: The value to match against
        :return: True if values are equal, False otherwise
        """
        if actual_value is None:
            return False

        if not isinstance(actual_value, str):
            if isinstance(actual_value, int | float):
                return self._assert_equal(actual_value, expected_value)
            else:
                return False

        if actual_value != expected_value:
            return False
        return True

    def _assert_is_not(self, actual_value: Optional[str], expected_value: str) -> bool:
        """
        Check if the actual value does not equal the expected value.

        :param actual_value: The value to compare
        :param expected_value: The value to match against
        :return: True if values are not equal, False otherwise
        """
        if actual_value is None:
            return False

        if not isinstance(actual_value, str):
            if isinstance(actual_value, int | float):
                return self._assert_not_equal(actual_value, expected_value)
            else:
                return False

        if actual_value == expected_value:
            return False
        return True

    def _assert_empty(self, actual_value: Any, input_condition: dict) -> bool:
        """
        Check if the actual value is empty based on its type.

        :param actual_value: The value to check for emptiness
        :param input_condition: Dictionary to update expected_value for frontend display
        :return: True if the value is empty, False otherwise
        """
        if isinstance(actual_value, list):
            input_condition["expected_value"] = []
            return len(actual_value) == 0
        elif isinstance(actual_value, bool):
            input_condition["expected_value"] = False
            return actual_value is False
        elif isinstance(actual_value, int) or isinstance(actual_value, float):
            input_condition["expected_value"] = 0
            return actual_value == 0
        elif isinstance(actual_value, str):
            input_condition["expected_value"] = ""
            return actual_value.strip() == ""
        elif isinstance(actual_value, dict):
            input_condition["expected_value"] = {}
            return len(actual_value) == 0
        return False

    def _assert_not_empty(self, actual_value: Any, input_condition: dict) -> bool:
        """
        Check if the actual value is not empty based on its type.

        :param actual_value: The value to check for non-emptiness
        :param input_condition: Dictionary to update expected_value for frontend display
        :return: True if the value is not empty, False otherwise
        """
        return not self._assert_empty(actual_value, input_condition)

    def _assert_equal(
        self, actual_value: Optional[int | float], expected_value: str | int | float
    ) -> bool:
        """
        Check if the actual numeric value equals the expected value.

        :param actual_value: The numeric value to compare
        :param expected_value: The value to match against
        :return: True if values are equal, False otherwise
        """
        if actual_value is None:
            return False

        if not isinstance(actual_value, int | float):
            if isinstance(actual_value, str):
                return self._assert_is(actual_value, expected_value)
            else:
                return False

        if isinstance(actual_value, int):
            expected_value = int(expected_value)
        else:
            expected_value = float(expected_value)

        if actual_value != expected_value:
            return False
        return True

    def _assert_not_equal(
        self, actual_value: Optional[int | float], expected_value: str | int | float
    ) -> bool:
        """
        Check if the actual numeric value does not equal the expected value.

        :param actual_value: The numeric value to compare
        :param expected_value: The value to match against
        :return: True if values are not equal, False otherwise
        """
        if actual_value is None:
            return False

        if not isinstance(actual_value, int | float):
            if isinstance(actual_value, str):
                return self._assert_is_not(actual_value, expected_value)
            else:
                return False

        if isinstance(actual_value, int):
            expected_value = int(expected_value)
        else:
            expected_value = float(expected_value)

        if actual_value == expected_value:
            return False
        return True

    def _assert_greater_than(
        self, actual_value: Optional[int | float], expected_value: str | int | float
    ) -> bool:
        """
        Check if the actual numeric value is greater than the expected value.

        :param actual_value: The numeric value to compare
        :param expected_value: The value to compare against
        :return: True if actual_value > expected_value, False otherwise
        """
        if actual_value is None:
            return False

        if not isinstance(actual_value, int | float):
            return False

        if isinstance(actual_value, int):
            expected_value = int(expected_value)
        else:
            expected_value = float(expected_value)

        if actual_value <= expected_value:
            return False
        return True

    def _assert_less_than(
        self, actual_value: Optional[int | float], expected_value: str | int | float
    ) -> bool:
        """
        Check if the actual numeric value is less than the expected value.

        :param actual_value: The numeric value to compare
        :param expected_value: The value to compare against
        :return: True if actual_value < expected_value, False otherwise
        """
        if actual_value is None:
            return False

        if not isinstance(actual_value, int | float):
            return False

        if isinstance(actual_value, int):
            expected_value = int(expected_value)
        else:
            expected_value = float(expected_value)

        if actual_value >= expected_value:
            return False
        return True

    def _assert_greater_than_or_equal(
        self, actual_value: Optional[int | float], expected_value: str | int | float
    ) -> bool:
        """
        Check if the actual numeric value is greater than or equal to the expected value.

        :param actual_value: The numeric value to compare
        :param expected_value: The value to compare against
        :return: True if actual_value >= expected_value, False otherwise
        """
        if actual_value is None:
            return False

        if not isinstance(actual_value, int | float):
            return False

        if isinstance(actual_value, int):
            expected_value = int(expected_value)
        else:
            expected_value = float(expected_value)

        if actual_value < expected_value:
            return False
        return True

    def _assert_less_than_or_equal(
        self, actual_value: Optional[int | float], expected_value: str | int | float
    ) -> bool:
        """
        Check if the actual numeric value is less than or equal to the expected value.

        :param actual_value: The numeric value to compare
        :param expected_value: The value to compare against
        :return: True if actual_value <= expected_value, False otherwise
        """
        if actual_value is None:
            return False

        if not isinstance(actual_value, int | float):
            return False

        if isinstance(actual_value, int):
            expected_value = int(expected_value)
        else:
            expected_value = float(expected_value)

        if actual_value > expected_value:
            return False
        return True

    def _assert_null(self, actual_value: Any) -> bool:
        """
        Check if the actual value is null (None).

        :param actual_value: The value to check
        :return: True if the value is None, False otherwise
        """
        if actual_value is None:
            return True
        return False

    def _assert_not_null(self, actual_value: Any) -> bool:
        """
        Check if the actual value is not null (not None).

        :param actual_value: The value to check
        :return: True if the value is not None, False otherwise
        """
        if actual_value is not None:
            return True
        return False
