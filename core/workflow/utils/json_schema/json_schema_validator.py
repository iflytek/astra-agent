"""
JSON Schema validation utility with data preprocessing capabilities.

This module provides a comprehensive JSON Schema validator that can validate data
against schemas and automatically fix common data type issues.
"""

from typing import Any

import jsonschema  # type: ignore[import-untyped]
from loguru import logger


class JsonSchemaValidator:
    """
    JSON Schema validator with data preprocessing capabilities.

    This class provides validation and automatic data type correction
    for JSON data against defined schemas.
    """

    def __init__(self, schema: dict) -> None:
        """
        Initialize the validator with a JSON Schema.

        :param schema: JSON Schema definition dictionary
        """
        self.schema = schema

    def validate(self, data: dict) -> bool:
        """
        Validate data against the JSON Schema.

        :param data: Data dictionary to validate
        :return: True if validation passes, False otherwise
        """
        try:
            jsonschema.validate(instance=data, schema=self.schema)
            return True
        except jsonschema.ValidationError as e:
            logger.error(f"Validation error: {e.message}")
            return False

    def preprocess_data(self, data: dict) -> dict:
        """
        Preprocess input data according to JSON Schema, adding default values for
        required fields.

        :param data: Data dictionary to preprocess
        :return: Preprocessed data dictionary
        """
        processed_data = data.copy()
        required_fields = self.schema.get("required", [])
        properties = self.schema.get("properties", {})

        for key in required_fields:
            if key not in processed_data:
                # Add default values for required fields
                processed_data[key] = self._generate_default_value(
                    properties.get(key, {})
                )
            else:
                # Fix data types for required fields
                processed_data[key] = self._fix_type(
                    processed_data[key], properties.get(key, {})
                )

        return processed_data

    def _fix_type(self, value: Any, props: dict) -> Any:
        """
        Fix the data type of a single field value.

        :param value: Actual value to fix
        :param props: Schema definition for the current field
        :return: Fixed value with correct type
        """
        expected_type = props.get("type")

        match expected_type:
            case "integer":
                try:
                    return int(float(value))
                except (ValueError, TypeError):
                    return self._generate_default_value(props)

            case "number":
                try:
                    return float(value)
                except (ValueError, TypeError):
                    return self._generate_default_value(props)

            case "array":
                items_props = props.get("items", {})
                if isinstance(value, list):
                    return [self._fix_type(v, items_props) for v in value]
                return [self._fix_type(value, items_props)]

            case "string":
                if isinstance(value, str):
                    return value
                return str(value)  # Convert to string

            case "boolean":
                # Fix to boolean value
                if isinstance(value, bool):
                    return value
                return False  # Return False for other cases

            case "object":
                if isinstance(value, dict):
                    return value
                return {}  # Return empty dict for non-dict values

            case _:
                # If no specific processing logic, return original value
                return value

    def _generate_default_value(self, props: dict) -> Any:
        """
        Generate default value based on field type.

        :param props: Schema definition for the current field
        :return: Default value for the field type
        """
        default_values = {
            "integer": 0,
            "number": 0.0,
            "string": "",
            "array": [],
            "object": {},
            "boolean": False,
        }
        return default_values.get(props.get("type", ""))

    def validate_and_fix(self, data: dict) -> tuple[bool, dict]:
        """
        Validate and fix data in one operation.

        :param data: Data dictionary to process
        :return: Tuple of (validation_result, fixed_data)
        """
        fixed_data = self.preprocess_data(data)
        is_valid = self.validate(fixed_data)
        return is_valid, fixed_data
