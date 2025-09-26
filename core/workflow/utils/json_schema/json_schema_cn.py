"""
Chinese JSON Schema validators with localized error messages.

This module provides custom JSON Schema validators that generate
Chinese error messages for better user experience in Chinese applications.
"""

import re
from typing import Any, Iterator

from jsonschema import Draft7Validator  # type: ignore[import-untyped]
from jsonschema import ValidationError, validators


# Custom type validator with Chinese error messages
def cn_type_validator(
    validator: Any, types: Any, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom type validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param types: Expected type(s)
    :param instance: Value being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if not validator.is_type(instance, types):
        yield ValidationError(
            f"字段类型应为 {types}，但实际为 {type(instance).__name__}"
        )


# Custom required field validator with Chinese error messages
def cn_required_validator(
    validator: Any, required: list, instance: dict, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom required field validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param required: List of required field names
    :param instance: Dictionary being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    for req in required:
        if req not in instance:
            yield ValidationError(f"缺少必填字段: '{req}'")


def cn_allOf_validator(
    validator: Any, allOf: list, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom allOf validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param allOf: List of schemas that must all be satisfied
    :param instance: Value being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    for idx, subschema in enumerate(allOf):
        for error in validator.descend(instance, subschema, path=idx):
            yield ValidationError(
                f"必须同时满足 allOf 中的所有条件，第 {idx + 1} 个不符合: {error.message}"
            )


def cn_anyOf_validator(
    validator: Any, anyOf: list, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom anyOf validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param anyOf: List of schemas where at least one must be satisfied
    :param instance: Value being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if not any(validator.is_valid(instance, subschema) for subschema in anyOf):
        yield ValidationError("必须至少满足 anyOf 中的一个条件")


def cn_oneOf_validator(
    validator: Any, oneOf: list, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom oneOf validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param oneOf: List of schemas where exactly one must be satisfied
    :param instance: Value being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    valid = [validator.is_valid(instance, subschema) for subschema in oneOf]
    if valid.count(True) != 1:
        yield ValidationError("必须且仅满足 oneOf 中的一个条件")


def cn_not_validator(
    validator: Any, not_schema: dict, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom not validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param not_schema: Schema that must not be satisfied
    :param instance: Value being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if validator.is_valid(instance, not_schema):
        yield ValidationError("不允许匹配 not 中定义的模式")


def cn_enum_validator(
    validator: Any, enums: list, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom enum validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param enums: List of allowed enum values
    :param instance: Value being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if instance not in enums:
        yield ValidationError(f"值必须是以下枚举值之一: {enums}")


def cn_format_validator(
    validator: Any, format: str, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom format validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param format: Expected format string
    :param instance: Value being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if hasattr(validator, "FORMAT_CHECKER"):
        checker = validator.FORMAT_CHECKER
        if not checker.check(instance, format):
            yield ValidationError(f"字段格式不符合要求: {format}")


def cn_items_validator(
    validator: Any, items: dict, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom items validator that validates array elements.

    :param validator: JSON Schema validator instance
    :param items: Schema for array items
    :param instance: Array being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if isinstance(instance, list):
        for idx, item in enumerate(instance):
            for error in validator.descend(item, items, path=idx):
                yield error


def cn_maxItems_validator(
    validator: Any, max_items: int, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom maxItems validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param max_items: Maximum number of items allowed
    :param instance: Array being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if isinstance(instance, list) and len(instance) > max_items:
        yield ValidationError(
            f"数组元素数量不能超过 {max_items} 个，当前为 {len(instance)}"
        )


def cn_minItems_validator(
    validator: Any, min_items: int, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom minItems validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param min_items: Minimum number of items required
    :param instance: Array being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if isinstance(instance, list) and len(instance) < min_items:
        yield ValidationError(
            f"数组元素数量不能少于 {min_items} 个，当前为 {len(instance)}"
        )


def cn_maxLength_validator(
    validator: Any, max_length: int, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom maxLength validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param max_length: Maximum string length allowed
    :param instance: String being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if isinstance(instance, str) and len(instance) > max_length:
        yield ValidationError(
            f"字符串长度不能超过 {max_length}，当前为 {len(instance)}"
        )


def cn_minLength_validator(
    validator: Any, min_length: int, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom minLength validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param min_length: Minimum string length required
    :param instance: String being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if isinstance(instance, str) and len(instance) < min_length:
        yield ValidationError(
            f"字符串长度不能少于 {min_length}，当前为 {len(instance)}"
        )


def cn_maximum_validator(
    validator: Any, maximum: Any, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom maximum validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param maximum: Maximum value allowed
    :param instance: Number being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if isinstance(instance, (int, float)) and instance > maximum:
        yield ValidationError(f"数值不能大于 {maximum}，当前为 {instance}")


def cn_minimum_validator(
    validator: Any, minimum: Any, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom minimum validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param minimum: Minimum value required
    :param instance: Number being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if isinstance(instance, (int, float)) and instance < minimum:
        yield ValidationError(f"数值不能小于 {minimum}，当前为 {instance}")


def cn_pattern_validator(
    validator: Any, pattern: str, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom pattern validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param pattern: Regular expression pattern
    :param instance: String being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if isinstance(instance, str) and not re.search(pattern, instance):
        yield ValidationError(f"字符串不匹配正则表达式: {pattern}")


def cn_properties_validator(
    validator: Any, properties: dict, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom properties validator that validates object properties.

    :param validator: JSON Schema validator instance
    :param properties: Schema definitions for object properties
    :param instance: Object being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if isinstance(instance, dict):
        for prop, subschema in properties.items():
            if prop in instance:
                for error in validator.descend(instance[prop], subschema, path=prop):
                    yield error


def cn_contains_validator(
    validator: Any, subschema: dict, instance: Any, schema: dict
) -> Iterator[ValidationError]:
    """
    Custom contains validator that generates Chinese error messages.

    :param validator: JSON Schema validator instance
    :param subschema: Schema that at least one array item must satisfy
    :param instance: Array being validated
    :param schema: Schema definition
    :return: Iterator of validation errors
    """
    if isinstance(instance, list):
        if not any(validator.is_valid(item, subschema) for item in instance):
            yield ValidationError("数组中必须至少包含一个符合条件的元素")


# Create custom validator class with Chinese error messages
_CustomValidator = validators.extend(
    Draft7Validator,
    {
        "type": cn_type_validator,
        "required": cn_required_validator,
        "allOf": cn_allOf_validator,
        "anyOf": cn_anyOf_validator,
        "oneOf": cn_oneOf_validator,
        "not": cn_not_validator,
        "enum": cn_enum_validator,
        "format": cn_format_validator,
        "items": cn_items_validator,
        "maxItems": cn_maxItems_validator,
        "minItems": cn_minItems_validator,
        "maxLength": cn_maxLength_validator,
        "minLength": cn_minLength_validator,
        "maximum": cn_maximum_validator,
        "minimum": cn_minimum_validator,
        "pattern": cn_pattern_validator,
        "properties": cn_properties_validator,
        "contains": cn_contains_validator,
    },
)


def translate_error(error: ValidationError) -> str:
    """
    Translate validation error to Chinese message.

    :param error: ValidationError instance
    :return: Chinese error message string
    """
    keyword = str(error.validator)  # e.g., 'required', 'type', etc.
    message_map = {
        "type": f"字段类型应为 {error.validator_value}，但实际为 {type(error.instance).__name__}",
        "required": f"缺少必填字段: {error.message.split()[0]}",
        "maximum": f"数值不能大于 {error.validator_value}，当前为 {error.instance}",
        "minimum": f"数值不能小于 {error.validator_value}，当前为 {error.instance}",
        "maxLength": f"字符串长度不能超过 {error.validator_value}，"
        f"当前为 "
        f"{len(error.instance) if isinstance(error.instance, str) else 0}",
        "minLength": f"字符串长度不能少于 {error.validator_value}，"
        f"当前为 "
        f"{len(error.instance) if isinstance(error.instance, str) else 0}",
        "pattern": f"字符串不匹配正则表达式: {error.validator_value}",
        "enum": f"值必须是以下枚举值之一: {error.validator_value}，当前为 {error.instance}",
        "maxItems": f"数组元素数量不能超过 {error.validator_value} 个，"
        f"当前为 "
        f"{len(error.instance) if isinstance(error.instance, list) else 0}",
        "minItems": f"数组元素数量不能少于 {error.validator_value} 个，"
        f"当前为 "
        f"{len(error.instance) if isinstance(error.instance, list) else 0}",
        "anyOf": "必须至少满足 anyOf 中的一个条件",
        "allOf": "必须满足 allOf 中的所有条件",
        "oneOf": "必须且仅满足 oneOf 中的一个条件",
        "not": "不允许匹配 not 中定义的模式",
        "contains": "数组中必须至少包含一个符合条件的元素",
        "exclusiveMaximum": f"{error.instance} 大于或等于设定的最大值 {error.validator_value}",
        "exclusiveMinimum": f"{error.instance} 小于或等于设定的最小值 {error.validator_value}",
    }
    return message_map.get(keyword, error.message)


# Wrapper class for Chinese JSON Schema validation
class CNValidator:
    """
    Chinese JSON Schema validator wrapper class.

    This class provides a convenient interface for JSON Schema validation
    with Chinese error messages.
    """

    def __init__(self, schema: dict):
        """
        Initialize the validator with a JSON Schema.

        :param schema: JSON Schema definition dictionary
        """
        self.schema = schema
        # Use standard Draft7Validator for now
        self.validator = Draft7Validator(schema)

    def validate(self, instance: dict) -> list:
        """
        Validate instance against schema and return list of errors.

        :param instance: Data to validate
        :return: List of validation errors
        """
        return list(self.iter_errors(instance))

    def iter_errors(self, instance: dict) -> Iterator[dict]:
        """
        Iterate over validation errors with Chinese messages.

        :param instance: Data to validate
        :return: Iterator of error dictionaries
        """
        for error in self.validator.iter_errors(instance):
            error.message = translate_error(error)
            yield {
                "path": list(error.path),
                "schema_path": list(error.schema_path),
                "message": error.message,
            }
