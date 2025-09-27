import json
import re
from typing import Any, Literal, Optional, Union

from pydantic import BaseModel, Field
from workflow.consts.engine.template import TemplateSplitType
from workflow.consts.engine.value_type import ValueType
from workflow.engine.entities.variable_pool import RefNodeInfo, VariablePool
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span
from workflow.infra.providers.llm.iflytek_spark.const import RespFormatEnum


def process_array(name: str) -> str:
    """
    Extract array name from array access expression.

    :param name: Array access expression like 'array_name[0]'
    :return: Array name without index brackets
    """
    bracket_left_index = name.find("[")
    array_name = name[0:bracket_left_index]
    return array_name


def parse_nested_array(arr: list, index_str: str) -> Union[Any, None]:
    """
    Parse nested array values based on string notation (e.g., 'arr_arr_input[0][0]').

    This function extracts values from nested arrays using string-based index expressions.

    :param arr: Target nested array
    :param index_str: String representation of index expression (e.g., 'arr_arr_input[0][0]')
    :return: Parsed value from the nested array
    """
    import re

    # Extract indices from array expression, e.g., 'arr_arr_input[0][0]' -> ['0', '0']
    index_list = re.findall(r"\[(\d+)\]", index_str)
    # Convert indices to integer list
    indices = [int(i) for i in index_list]
    # Iteratively parse array values using extracted indices
    result = arr
    for idx in indices:
        result = result[idx]
    return result


def process_prompt(
    node_id: str, key_name: str, variable_pool: VariablePool, span: Span
) -> Union[Any | None]:
    """
    Process various types of variables including complex nested structures.

    This function handles variable names with complex access patterns such as:
    - valid.match
    - array[0]
    - another.valid[1].match
    - input[0].xx1[0].xxx1[0]

    :param node_id: ID of the current node
    :param key_name: Variable name with potential nested access
    :param variable_pool: Pool containing variables and their values
    :param span: Tracing span for monitoring
    :return: Resolved variable value or None if not found
    :raises: CustomException if variable parsing fails
    """

    try:
        key_name_parts = key_name.split(".")
        last_part: Any = None
        for index, cur_part_key_name in enumerate(key_name_parts):
            arr_name = (
                process_array(cur_part_key_name)
                if "[" in cur_part_key_name
                else cur_part_key_name
            )
            try:
                last_part = (
                    variable_pool.get_variable(
                        node_id=node_id, key_name=arr_name, span=span
                    )
                    if index == 0
                    else last_part.get(arr_name)
                )
            except Exception:
                # User's key_name is incorrect and not found in variable pool
                return key_name
            last_part = (
                parse_nested_array(last_part, cur_part_key_name)
                if "[" in cur_part_key_name
                else last_part
            )
        return last_part
    except Exception as e:
        raise CustomException(
            err_code=CodeEnum.VARIABLE_PARSE_ERROR,
            err_msg=f"Variable name: {key_name} parsing failed, reason: {str(e)}",
        ) from e


def prompt_template_replace(
    input_identifier: list,
    _prompt_template: str,
    node_id: str,
    variable_pool: VariablePool,
    span_context: Span,
) -> str:
    """
    Replace variables in prompt template with their actual values.

    This function processes a prompt template by finding all variables,
    resolving their values from the variable pool, and replacing them
    in the template string.

    :param input_identifier: List of valid input variable identifiers
    :param _prompt_template: Template string containing variables
    :param node_id: ID of the current node
    :param variable_pool: Pool containing variables and their values
    :param span_context: Tracing span for monitoring
    :return: Template with variables replaced by their values
    """
    available_placeholders = PromptUtils.get_available_placeholders(
        node_id, _prompt_template, variable_pool, span_context
    )
    replacements = {}
    for var_name in available_placeholders:
        var_name_list = re.split(r"[\[.\]]", var_name)
        if var_name_list[0].strip() in input_identifier:
            replacements.update(
                {
                    var_name: process_prompt(
                        node_id=node_id,
                        key_name=var_name,
                        variable_pool=variable_pool,
                        span=span_context,
                    )
                }
            )

    replacements_str = {}
    for key, value in replacements.items():
        try:
            if not isinstance(value, str):
                # Convert non-string values to JSON format for template replacement
                value = json.dumps(value, ensure_ascii=False)
        except Exception:
            value = ""
        replacements_str[key] = value

    # Replace variables in template with resolved values
    _prompt_template = PromptUtils.replace_variables(_prompt_template, replacements_str)
    return _prompt_template


class TemplateUnitObj(BaseModel):
    """
    Object representing a unit in a template string.

    This class represents either a constant string or a variable reference
    within a template, along with metadata about its type and dependencies.

    :param key: The value for constants or the name for variables
    :param key_type: Type indicator (0=constant, 1=variable)
    :param is_end: Whether this is the final part of the template
    :param dep_node_id: ID of the referenced node when this is a variable
    :param ref_var_name: Original name of the referenced variable
    """

    key: str = Field(default="")  # Value for constants or name for variables
    key_type: Literal[0, 1, 2] = Field(default=0)  # 0: constant; 1: variable
    value: str = Field(default="")
    is_end: bool = Field(
        default=False
    )  # Whether this is the final output part of the template
    ref_node_info: Optional[RefNodeInfo] = None

    class Config:
        arbitrary_types_allowed = True


class PromptUtils:

    @staticmethod
    def get_placeholders(template: str) -> list[str]:
        """
        Get placeholders from template.

        :param template: Template string containing variables
        :return: List of placeholders
        """
        placeholders: list[str] = []

        # Step1 : Extract content between {{ ... }}
        braces_pattern = re.compile(r"\{\{(.*?)}}")
        raw_matches = braces_pattern.findall(template)

        # Step2: Define variable name rules
        # Single name: letters, numbers, underscores, hyphens
        name_pattern = r"[A-Za-z0-9_-]+"
        # Optional array index: multiple [numbers], allow negative numbers
        index_pattern = r"(?:\[-?\d+\])*"
        # One complete segment: name + optional index
        segment_pattern = rf"{name_pattern}{index_pattern}"
        # Multiple segments connected by dots
        variable_pattern = re.compile(rf"^{segment_pattern}(?:\.{segment_pattern})*$")

        # Step3: Filter valid variable names
        for key in raw_matches:
            if not variable_pattern.match(key):
                continue
            placeholders.append(key)
        return placeholders

    @staticmethod
    def get_available_placeholders(
        node_id: str, template: str, variable_pool: VariablePool, span: Span
    ) -> list[str]:
        """
        Get available placeholders from template.

        :param node_id: ID of the current node
        :param template: Template string containing variables
        :param variable_pool: Pool containing variables and their values
        :param span: Tracing span for monitoring
        :return: List of available placeholders
        """
        placeholders = PromptUtils.get_placeholders(template)
        available_placeholders: list[str] = []
        for placeholder in placeholders:
            dep_node_id = variable_pool.get_variable_ref_node_id(
                node_id, placeholder, span
            ).ref_node_id
            if dep_node_id:
                available_placeholders.append(placeholder)
        return placeholders

    @staticmethod
    def get_template_unit(
        node_id: str, template: str, variable_pool: VariablePool, span: Span
    ) -> list[TemplateUnitObj]:
        """
        Get template unit list from template.

        :param node_id: ID of the current node
        :param template: Template string containing variables
        :param variable_pool: Pool containing variables and their values
        :param span: Tracing span for monitoring
        :return: List of template units
        """
        if not template:
            return []

        template_unit_list: list[TemplateUnitObj] = []

        placeholders = PromptUtils.get_available_placeholders(
            node_id, template, variable_pool, span
        )
        placeholders_with_brackets = [
            f"{{{{{placeholder}}}}}" for placeholder in placeholders
        ]

        # Build the regularity to capture all delimiters
        pattern = "(" + "|".join(map(re.escape, placeholders_with_brackets)) + ")"
        parts = re.split(pattern, template)
        for i, part in enumerate(parts):

            # Handle placeholder information
            if part in placeholders_with_brackets:
                part_without_brackets = part.removeprefix("{{").removesuffix("}}")
                ref_node_info = variable_pool.get_variable_ref_node_id(
                    node_id, part_without_brackets, span
                )
                if not ref_node_info:
                    raise ValueError(
                        f"Node {node_id} has no variable {part_without_brackets}"
                    )

                template_unit = TemplateUnitObj(
                    key=part_without_brackets,
                    key_type=TemplateSplitType.VARIABLE.value,
                    ref_node_info=ref_node_info,
                )

                if ref_node_info.ref_var_type == ValueType.LITERAL.value:
                    template_unit.key_type = TemplateSplitType.CONSTS.value
                    template_unit.value = ref_node_info.literal_var_value

                if (
                    template_unit.ref_node_info
                    and template_unit.ref_node_info.ref_var_type == ValueType.REF.value
                    and template_unit.ref_node_info.llm_resp_format
                    == RespFormatEnum.JSON.value
                ):
                    # Mark as LLM JSON output if response format is JSON
                    template_unit.key_type = TemplateSplitType.LLM_JSON.value

            # Handle normal text information
            else:
                template_unit = TemplateUnitObj(
                    value=part,
                    key_type=TemplateSplitType.CONSTS.value,
                )

            if i == len(parts) - 1:
                template_unit.is_end = True

            template_unit_list.append(template_unit)

        return template_unit_list

    @staticmethod
    def replace_variables(prompt_template: str, replacements: dict) -> str:
        """
        Replace variables in prompt template using template unit parsing.

        This function parses the prompt template into template units and replaces
        variables with their corresponding values from the replacements dictionary.

        :param prompt_template: Template string containing variables
        :param replacements: Dictionary mapping variable names to their values
        :return: Template with variables replaced
        """
        for key, value in replacements.items():
            prompt_template = prompt_template.replace("{{" + key + "}}", value)
        return prompt_template
