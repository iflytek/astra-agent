"""JSON Schema Utilities Module.

This module provides utilities for loading and managing JSON schema files
used in the OpenStellar platform. It handles various schema types including
create tool schemas, update tool schemas, HTTP run schemas, tool debug schemas,
and MCP register schemas.

The module uses a SchemaProcess class to read schema files from the filesystem
and provides getter functions to access the loaded schemas.
"""

import json
import os

create_tool_schema: str = ""
update_tool_schema: str = ""
http_run_schema: str = ""
tool_debug_schema: str = ""
mcp_register_schema: str = ""


def load_create_tool_schema():
    """
    description: Load the tool's schema information
    :return:
    """
    global create_tool_schema
    dir_ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "schema_files")
    file_ = "create_tools_schema.json"
    schema_process_inst = SchemaProcess(dir_)
    schema_info = schema_process_inst(file_)
    create_tool_schema = schema_info


def load_update_tool_schema():
    """Load the update tool schema from JSON file.

    Loads the update tools schema configuration from the schema_files directory
    and stores it in the global update_tool_schema variable.

    Returns:
        None: Updates global variable update_tool_schema
    """
    global update_tool_schema
    dir_ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "schema_files")
    file_ = "update_tools_schema.json"
    schema_process_inst = SchemaProcess(dir_)
    schema_info = schema_process_inst(file_)
    update_tool_schema = schema_info
    # print(update_tool_schema)


def load_http_run_schema():
    """Load the HTTP run schema from JSON file.

    Loads the HTTP run schema configuration from the schema_files directory
    and stores it in the global http_run_schema variable.

    Returns:
        None: Updates global variable http_run_schema
    """
    global http_run_schema
    dir_ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "schema_files")
    file_ = "http_run_schema.json"
    schema_process_inst = SchemaProcess(dir_)
    schema_info = schema_process_inst(file_)
    http_run_schema = schema_info


def load_tool_debug_schema():
    """Load the tool debug schema from JSON file.

    Loads the tool debug schema configuration from the schema_files directory
    and stores it in the global tool_debug_schema variable.

    Returns:
        None: Updates global variable tool_debug_schema
    """
    global tool_debug_schema
    dir_ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "schema_files")
    file_ = "tool_debug_schema.json"
    schema_process_inst = SchemaProcess(dir_)
    schema_info = schema_process_inst(file_)
    tool_debug_schema = schema_info


def load_mcp_register_schema():
    """Load the MCP register schema from JSON file.

    Loads the MCP register schema configuration from the schema_files directory
    and stores it in the global mcp_register_schema variable.

    Returns:
        None: Updates global variable mcp_register_schema
    """
    global mcp_register_schema
    dir_ = os.path.join(os.path.dirname(os.path.abspath(__file__)), "schema_files")
    file_ = "mcp_register_schema.json"
    schema_process_inst = SchemaProcess(dir_)
    schema_info = schema_process_inst(file_)
    mcp_register_schema = schema_info


def get_http_run_schema():
    """Get the loaded HTTP run schema.

    Returns:
        str: The HTTP run schema as a string
    """
    return http_run_schema


def get_tool_debug_schema():
    """Get the loaded tool debug schema.

    Returns:
        str: The tool debug schema as a string
    """
    return tool_debug_schema


def get_create_tool_schema() -> str:
    """
    description: Get create_tool_schema
    :return:
    """
    return create_tool_schema


def get_update_tool_schema() -> str:
    """Get the loaded update tool schema.

    Returns:
        str: The update tool schema as a string
    """
    return update_tool_schema


def get_mcp_register_schema() -> str:
    """Get the loaded MCP register schema.

    Returns:
        str: The MCP register schema as a string
    """
    return mcp_register_schema


class SchemaProcess:
    """Schema file processor for loading JSON schema files.

    This class handles the reading and processing of JSON schema files
    from a specified directory path.
    """

    def __init__(self, dir_path: str):
        """
        description: Initialize
        :param dir_path:
        """
        self.path = dir_path

    def __call__(self, file: str):
        """
        description: Synchronous call, read file information
        :return:
        """
        if not file.endswith(".json"):
            raise Exception("file %s suffix not .json" % file)
        schema_info = None
        if not os.path.exists(os.path.join(self.path, file)):
            raise Exception("file %s not exit in dir %s" % (file, self.path))
        with open(os.path.join(self.path, file), encoding="utf8") as file_handle:
            schema_info = file_handle.read()

        if not schema_info:
            raise Exception("file %s is null" % file)
        return schema_info


if __name__ == "__main__":
    import jsonschema

    # validate_data = {
    #     "header": {
    #         "app_id": "xxxx",
    #         "uid": "xxxxx"
    #     },
    #     "parameter": {
    #         "chat": {
    #             "domain": "generalv3.5",
    #             "temperature": 0.1,
    #             "max_tokens": 1024,
    #             "top_k": 3,
    #             "question_type": "not_knowledge",
    #             "function_call": True,
    #             "maas_api": 1,
    #             "patch_id": []
    #         },
    #         "tool": {
    #             "http_request": True,
    #             "tool_ids": [
    #                 {
    #                     "id": "tool-link@1222",
    #                     "operation_ids": [
    #                         {
    #                             # "id": "xxxxxxx",
    #                             # "fallback_value": {
    #                             #     "params": [
    #                             #         {
    #                             #             "key": "xxxxx",
    #                             #             "value": "xxxxx"
    #                             #         }
    #                             #     ],
    #                             #     "header": [
    #                             #         {
    #                             #             "key": "xxxxx",
    #                             #             "value": "xxxxx"
    #                             #         }
    #                             #     ],
    #                             #     "body": [
    #                             #         {
    #                             #             "key": "xxxxx",
    #                             #             "value": "xxxxx"
    #                             #         }
    #                             #     ]
    #                             # },
    #                             "constant_value": {
    #                                 "params": [
    #                                     {
    #                                         "key": "xxxxx",
    #                                         "value": "xxxxx"
    #                                     }
    #                                 ],
    #                                 "header": [
    #                                     {
    #                                         "key": "xxxxx",
    #                                         "value": "xxxxx"
    #                                     }
    #                                 ],
    #                                 "body": [
    #                                     {
    #                                         "key": "xxxxx",
    #                                         "value": "xxxxx"
    #                                     }
    #                                 ]
    #                             }
    #                         }
    #                     ]
    #                 }
    #             ]
    #         }
    #     },
    #     "payload": {
    #         "message": {
    #             "history_context": [
    #                 {
    #                     "role": "user",
    #                     "content": "你是谁？"
    #                 }
    #             ],
    #             "input": "xxxxxxxx"
    #         },
    #         "prompt": {
    #             "text": "xxxxxx",
    #             "output_type": 0,
    #             "placeholder": [
    #                 {
    #                     "key": "__history__",
    #                     "value": "$ref@/payload/message/history_context"
    #                 }
    #             ]
    #         }
    #     }
    # }
    # errs = list(validator.iter_errors(validate_data))
    load_update_tool_schema()
    validator = jsonschema.Draft7Validator(json.loads(get_update_tool_schema()))
    # load_create_tool_schema()
    # validator = jsonschema.Draft7Validator(json.loads(get_create_tool_schema()))
    errs = list(
        validator.iter_errors(
            {
                "header": {"app_id": "xxxxx"},
                "payload": {
                    "tools": [
                        {
                            "id": "tool@1232312",
                            "name": "xxxx",
                            "description": "xxxxxx",
                            "schema_type": 1,
                            "openapi_schema": "xxxx",
                        }
                        # {
                        #     "name": "xxxx",
                        #     "description": "xxxxxx",
                        #     "schema_type": 3,
                        #     "openapi_schema": "xxxx"
                        # },
                        # {
                        #     "name": "xxxx",
                        #     "description": "xxxxxx",
                        #     "schema_type": 1,
                        #     "openapi_schema": "xxxx"
                        # },
                    ]
                },
            }
        )
    )
    if errs:
        for err in errs:
            print(err.json_path, err.message)
    # validate = fastjsonschema.compile(json.loads(get_create_tool_schema()))
    # validate.iter_errors(
    #     {
    #         "header": {
    #             "app_id": "xxxxx"
    #         }
    #     }
    # )

    # print(json.loads(get_create_tool_schema()))
