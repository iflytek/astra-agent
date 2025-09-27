"""OpenAPI schema parsing utilities.

This module provides classes and methods for parsing OpenAPI specifications,
extracting parameters, request bodies, and other schema information.
"""

import json

from plugin.link.exceptions.sparklink_exceptions import SparkLinkOpenapiSchemaException
from plugin.link.utils.errors.code import ErrCode
from plugin.link.utils.open_api_schema.types.schema_parser_types import ParamsConfig
from plugin.link.utils.otlp.trace.span import Span


class OpenapiSchemaParser:
    """Parser for OpenAPI schema specifications.

    This class provides methods to parse OpenAPI schemas and extract
    configuration information for API endpoints, parameters, and request bodies.
    """

    span: Span | None = None

    def __init__(self, schema, span: Span = None):
        self.schema = schema
        self.span = span

    @classmethod
    def schema_params_config_parser(cls, params, span):
        """
        description: 解析openapi schema params 配置信息
        """
        if span:
            with span.start(
                func_name="OpenapiSchemaParser.schema_params_config_parser"
            ) as span_context:
                params_config = {}
                params_schema_info = params.get("schema")
                span_context.add_info_events(
                    {"input": json.dumps(params, ensure_ascii=False)}
                )
                if "type" in params_schema_info:
                    params_config.update({"type": params_schema_info["type"]})
                if "description" in params_schema_info:
                    params_config.update(
                        {"description": params_schema_info["description"]}
                    )
                if "default" in params_schema_info:
                    params_config.update({"default": params_schema_info["default"]})
                if "enum" in params_schema_info:
                    params_config.update({"enum": params_schema_info["enum"]})
                if "pattern" in params_schema_info:
                    params_config.update({"pattern": params_schema_info["pattern"]})
                return params_config, params.get("required", False)

    @classmethod
    def schema_params_parser(cls, params, span):
        """Parse OpenAPI parameters into organized schema objects.

        Args:
            params: List of parameter objects from OpenAPI spec
            span: Tracing span for observability

        Returns:
            Tuple of (path_schema, query_schema, header_schema) ParamsConfig objects
        """
        if span:
            with span.start(
                func_name="OpenapiSchemaParser.schema_params_parser"
            ) as span_context:
                header = {}
                header_required = []
                path = {}
                path_required = []
                query = {}
                query_required = []
                for parameter in params:
                    params_key = parameter.get("name")
                    params_config, required = (
                        OpenapiSchemaParser.schema_params_config_parser(
                            parameter, span_context
                        )
                    )
                    if parameter.get("in") == "header":
                        header.update({params_key: params_config})
                        if required:
                            header_required.append(params_key)
                    elif parameter.get("in") == "query":
                        query.update({params_key: params_config})
                        if required:
                            query_required.append(params_key)
                    elif parameter.get("in") == "path":
                        path.update({params_key: params_config})
                        if required:
                            path_required.append(params_key)
                header_schema: ParamsConfig = ParamsConfig(
                    type="object",
                    properties=header,
                    required=header_required if header_required else [],
                )
                path_schema: ParamsConfig = ParamsConfig(
                    type="object",
                    properties=path,
                    required=path_required if path_required else [],
                )
                query_schema: ParamsConfig = ParamsConfig(
                    type="object",
                    properties=query,
                    required=query_required if query_required else [],
                )
                span_context.add_info_events(
                    {"header": json.dumps(header_schema.to_dict(), ensure_ascii=False)}
                )
                span_context.add_info_events(
                    {"path": json.dumps(path_schema.to_dict(), ensure_ascii=False)}
                )
                span_context.add_info_events(
                    {"query": json.dumps(query_schema.to_dict(), ensure_ascii=False)}
                )
                return path_schema, query_schema, header_schema

    @classmethod
    def process_schema(cls, body_schema: dict, span) -> dict:
        """
        description: 解析 application/json中schema的内容
        """
        properties = {}
        type = body_schema.get("type")
        if type == "array":
            body_items = body_schema.get("items", {})
            # Recursive
            _properties = cls.process_schema(body_items, span)
            properties["array"] = _properties
        else:
            body_properties = body_schema.get("properties", {})
            for parameter_name, parameter_detail in body_properties.items():
                parameter_type = parameter_detail.get("type")
                if parameter_type == "object" or parameter_type == "array":
                    # Recursive
                    _properties = cls.process_schema(parameter_detail, span)
                    properties[parameter_name] = _properties
                else:
                    properties[parameter_name] = parameter_type

        return properties

    @classmethod
    def schema_body_json_parser(cls, body, span):
        """
        description: 解析 application/json
        """
        if span:
            with span.start(
                func_name="OpenapiSchemaParser.schema_body_json_parser"
            ) as span_context:
                body_schema: dict = body.get("schema", {})
                span_context.add_info_events(
                    {"body": json.dumps(body_schema, ensure_ascii=False)}
                )
                # TODO: Use this when body parsing is needed later
                # body_schema_res = cls.process_schema(body_schema, span_context)
                # return body_schema_res
                return body_schema

    def _extract_basic_info(self, openapi: dict) -> dict:
        """Extract basic OpenAPI information."""
        bundles = {}
        openapi_info = openapi["info"]
        openapi_version = openapi["openapi"]
        bundles.update({"openapi_version": openapi_version})
        title = openapi_info.get("title", "")
        bundles.update({"tool_title": title})
        description = openapi_info.get("description", "")
        bundles.update({"tool_description": description})
        return bundles

    def _validate_and_get_server_url(self, openapi: dict) -> str:
        """Validate server configuration and return server URL."""
        if len(openapi["servers"]) == 0:
            raise SparkLinkOpenapiSchemaException(
                code=ErrCode.OPENAPI_SCHEMA_SERVER_NOT_EXIST_ERR.code,
                err_pre=ErrCode.OPENAPI_SCHEMA_SERVER_NOT_EXIST_ERR.msg,
                err="找不到请求服务",
            )
        return openapi["servers"][0]["url"]

    def _extract_interfaces(self, openapi: dict) -> list:
        """Extract all interfaces from OpenAPI paths."""
        interfaces = []
        methods = ["get", "post", "put", "delete", "patch", "head", "options", "trace"]

        for path, path_item in openapi["paths"].items():
            for method in methods:
                if method in path_item:
                    interfaces.append(
                        {
                            "path": path,
                            "method": method,
                            "operation": path_item[method],
                        }
                    )
        return interfaces

    def _process_request_body_refs(self, interface: dict, openapi: dict) -> None:
        """Process $ref references in request body schemas."""
        request_body = interface.get("operation", {}).get("requestBody", {})
        for content_type, content in request_body.get("content", {}).items():
            if "schema" not in content:
                continue
            if "$ref" in content["schema"]:
                root = openapi
                reference = content["schema"]["$ref"].split("/")[1:]
                for ref in reference:
                    root = root[ref]
                # overwrite the content
                interface["operation"]["requestBody"]["content"][content_type][
                    "schema"
                ] = root

    def _process_interface_schemas(
        self, interface: dict, api_key_info: dict, openapi: dict, span_context
    ) -> dict:
        """Process all schemas for a single interface."""
        path_schema = None
        query_schema = None
        header_schema = None
        request_body_schema = None
        security_info = None
        security_type = None

        # Process security
        if "security" in interface["operation"]:
            security_info = api_key_info
            for k, _ in interface["operation"]["security"][0].items():
                security_type = k

        # Process parameters
        if "parameters" in interface["operation"]:
            path_schema, query_schema, header_schema = self.schema_params_parser(
                interface["operation"]["parameters"], span=span_context
            )

        # Process request body
        self._process_request_body_refs(interface, openapi)
        request_body = interface.get("operation", {}).get("requestBody", {})
        for content_type, content in request_body.get("content", {}).items():
            if content_type == "application/json":
                request_body_schema = self.schema_body_json_parser(
                    content, span=span_context
                )
            else:
                raise SparkLinkOpenapiSchemaException(
                    code=ErrCode.OPENAPI_SCHEMA_BODY_TYPE_ERR.code,
                    err_pre=ErrCode.OPENAPI_SCHEMA_BODY_TYPE_ERR.msg,
                    err=f"openapi schema 当前不支持{content_type}请求体",
                )

        return {
            "path_schema": path_schema,
            "query_schema": query_schema,
            "header_schema": header_schema,
            "request_body_schema": request_body_schema,
            "security_info": security_info,
            "security_type": security_type,
        }

    def _build_operation_bundle(
        self, interface: dict, schemas: dict, server_url: str
    ) -> tuple:
        """Build operation bundle for a single interface."""
        path = interface["path"]
        method = interface["method"]
        operation_id = interface["operation"]["operationId"]

        if "description" in interface["operation"]:
            operation_description = interface["operation"]["description"]
        elif "summary" in interface["operation"]:
            operation_description = interface["operation"]["summary"]
        else:
            operation_description = ""

        full_server_url = server_url + path

        operation_bundle = {
            "server_url": full_server_url,
            "method": method,
            "operation_description": operation_description,
            "header": (
                schemas["header_schema"].to_dict()
                if schemas["header_schema"] and schemas["header_schema"].properties
                else {}
            ),
            "path": (
                schemas["path_schema"].to_dict()
                if schemas["path_schema"] and schemas["path_schema"].properties
                else {}
            ),
            "query": (
                schemas["query_schema"].to_dict()
                if schemas["query_schema"] and schemas["query_schema"].properties
                else {}
            ),
            "body": (
                schemas["request_body_schema"]
                if schemas["request_body_schema"]
                and schemas["request_body_schema"].get("properties", {})
                else {}
            ),
            "security": schemas["security_info"],
            "security_type": schemas["security_type"],
        }

        return operation_id, operation_bundle

    def schema_parser(self):
        """
        解析 schema
        """
        if not self.span:
            return
        with self.span.start(
            func_name="OpenapiSchemaParser.schema_parser"
        ) as span_context:
            openapi = self.schema

            # Extract basic information
            bundles = self._extract_basic_info(openapi)

            # Validate and get server URL
            server_url = self._validate_and_get_server_url(openapi)

            # Get authentication information from components
            components = openapi.get("components", {})
            api_key_info = components.get("securitySchemes", {})

            # Extract all interfaces
            interfaces = self._extract_interfaces(openapi)

            # Process each interface
            operation_ids = []
            for interface in interfaces:
                # Process schemas for this interface
                schemas = self._process_interface_schemas(
                    interface, api_key_info, openapi, span_context
                )

                # Build operation bundle
                operation_id, operation_bundle = self._build_operation_bundle(
                    interface, schemas, server_url
                )

                operation_ids.append(operation_id)
                bundles.update({operation_id: operation_bundle})

            bundles.update({"operation_ids": operation_ids})
            span_context.add_info_events(
                {"bundles": json.dumps(bundles, ensure_ascii=False)}
            )
            return bundles


if __name__ == "__main__":
    test_schema = {
        "schema": {
            "type": "object",
            "required": ["lat", "long"],
            "properties": {
                "lat": {"type": "number"},
                "long": {"type": "number"},
                "hhhhhh": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "a": {"type": "number"},
                            "b": {"type": "string"},
                            "c": {
                                "type": "object",
                                "properties": {
                                    "hello": {"type": "string"},
                                    "world": {"type": "string"},
                                },
                            },
                        },
                    },
                },
            },
        }
    }

    test_span = Span()
    parser = OpenapiSchemaParser(schema=test_schema, span=test_span)
    res = parser.schema_body_json_parser(test_schema, test_span)
    print(res)
