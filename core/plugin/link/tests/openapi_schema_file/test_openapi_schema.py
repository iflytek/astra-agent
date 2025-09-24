"""OpenAPI Schema Validation Test Module.

This module provides functionality to validate OpenAPI schema files using the
openapi-spec-validator library. It demonstrates how to load JSON schema files,
parse them, and perform validation to ensure compliance with OpenAPI specifications.
"""

import json

from openapi_spec_validator import validate
from openapi_spec_validator.validation.exceptions import OpenAPIValidationError
from plugin.link.utils.json_schemas.read_json_schemas import SchemaProcess

if __name__ == "__main__":
    dir_ = "./"
    file_ = "test.json"
    schema_process_inst = SchemaProcess(dir_)
    schema_info = schema_process_inst(file_)
    try:
        if not isinstance(schema_info, dict):
            schema_info = json.loads(schema_info)
        validate(schema_info)
    except OpenAPIValidationError as err:
        errs = [{"error_path": err.json_path, "error_message": err.message}]
        print(errs)
