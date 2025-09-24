"""JSON Schema Validation Module.

This module provides functionality for validating JSON data against JSON schemas
using the jsonschema library. It includes utilities for API input parameter validation
and comprehensive error reporting for schema validation failures.
"""

import json

import jsonschema


def api_validate(schema_: str, data_: dict):
    """
    校验 api 入参
    :param schema_:
    :param data_:
    :return:
    """
    schema_json = json.loads(schema_)
    validator = jsonschema.Draft7Validator(schema_json)
    errs = list(validator.iter_errors(data_))
    err_info = []
    if errs:
        for err in errs:
            err_info.append(f"path: {err.json_path}, message: {err.message}")
    if err_info:
        return ";".join(err_info)
    return ""


# test
if __name__ == "__main__":
    FILE_PATH = "./schema_files/http_run_schema.json"
    with open(FILE_PATH, "r", encoding="utf-8") as file:
        schema = file.read()
        input = json.loads(
            r'{"header": {"app_id": "a01c2bc7"}, '
            r'"parameter": {"tool_id": "tool@81e142b05c21000", '
            r'"operation_id": "使用率明细-eab5uhoq"}, "payload": {"message": {}}}'
        )
        RESULT = api_validate(schema, input)
        print(RESULT)
