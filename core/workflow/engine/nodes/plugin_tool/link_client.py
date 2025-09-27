import json
import time
from base64 import b64encode
from typing import Any, Dict, List, Set, Tuple

import requests  # type: ignore
from workflow.exception.e import CustomException
from workflow.exception.errors.code_convert import CodeConvert
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.trace.span import Span


class Tool:
    """
    Represents a plugin tool that can be executed through the Link system.

    This class encapsulates a single tool operation with its schema, parameters,
    and execution capabilities. It handles parameter assembly and HTTP communication
    with external tool services.
    """

    def __init__(
        self,
        app_id: str,
        tool_id: str,
        operation_id: str,
        method_schema: dict,
        parameters: dict,
        get_url: str,
        run_url: str,
        version: str,
    ):
        """
        Initialize a Tool instance.

        :param app_id: Application identifier
        :param tool_id: Unique tool identifier
        :param operation_id: Specific operation identifier within the tool
        :param method_schema: OpenAPI schema for the tool method
        :param parameters: Tool parameter definitions
        :param get_url: URL for retrieving tool schema information
        :param run_url: URL for executing tool operations
        :param version: Tool version
        """
        self.app_id = app_id
        self.tool_id = tool_id
        self.operation_id = operation_id
        self.method_schema = method_schema
        self.parameters = parameters
        self.get_url = get_url
        self.run_url = run_url
        self.version = version

    def assemble_parameters(
        self, action_input: dict, business_input: dict
    ) -> Tuple[Dict[str, Any], Dict[str, Any], Dict[str, Any]]:
        """
        Assemble HTTP parameters from action and business inputs.

        This method processes the tool's parameter schema and extracts values
        from action_input and business_input to build header, query, and path
        parameters for the HTTP request.

        :param action_input: Action-specific input parameters
        :param business_input: Business-specific input parameters
        :return: Tuple containing (header_params, query_params, path_params)
        """
        header: Dict[str, Any] = {}
        query: Dict[str, Any] = {}
        path: Dict[str, Any] = {}
        parameters_schema = self.method_schema.get("parameters", [])
        # Process each parameter based on its location (header, query, path)
        for parameter in parameters_schema:
            if parameter["in"] == "header":
                self.update_params(header, parameter, action_input, business_input)
            elif parameter["in"] == "query":
                self.update_params(query, parameter, action_input, business_input)
            elif parameter["in"] == "path":
                self.update_params(path, parameter, action_input, business_input)
        return header, query, path

    @staticmethod
    def update_params(
        params: dict, header_parameter: dict, action_input: dict, business_input: dict
    ) -> None:
        """
        Update parameter dictionary with values from appropriate input source.

        This method determines the source of parameter values based on the x-from
        configuration and updates the parameter dictionary accordingly.

        :param params: Parameter dictionary to update
        :param header_parameter: Parameter schema definition
        :param action_input: Action-specific input parameters
        :param business_input: Business-specific input parameters
        """
        x_from = header_parameter.get("schema", {}).get("x-from")
        name = header_parameter.get("name", "unknown_field")
        default_value = header_parameter.get("schema", {}).get("default")

        if x_from == 0:  # Model recognition source
            value = action_input.get(name, default_value)
        elif x_from == 1:  # Business passthrough source
            value = business_input.get(name, default_value)
        else:
            # Default to action input, fallback to default value
            value = action_input.get(name, default_value)
        params[name] = value

    def assemble_body(
        self, body_schema: dict, action_input: dict, business_input: dict
    ) -> Dict[str, Any]:
        """
        Assemble request body from schema and input parameters.

        This method recursively processes the body schema to build the request
        body structure, handling nested objects and parameter value resolution.

        :param body_schema: OpenAPI schema for request body
        :param action_input: Action-specific input parameters
        :param business_input: Business-specific input parameters
        :return: Assembled request body dictionary
        """
        properties = {}
        body_properties = body_schema.get("properties", {})
        for parameter_name, parameter_detail in body_properties.items():
            parameter_type = parameter_detail.get("type")
            # TODO: Add validation for required parameters
            # required = parameter_detail.get("required", [])
            if parameter_type == "object":
                # Recursively process nested objects
                _properties = self.assemble_body(
                    parameter_detail,
                    action_input.get(parameter_name, {}),
                    business_input,
                )
                properties[parameter_name] = _properties
            else:
                x_from = parameter_detail.get("x-from")
                default_value = parameter_detail.get("default", None)
                # Determine value source based on x-from configuration
                if x_from == 0:
                    value = action_input.get(parameter_name, None)
                elif x_from == 1:
                    value = business_input.get(parameter_name, None)
                else:
                    value = action_input.get(parameter_name, None)

                # Use default value if no value found
                if value is None:
                    if default_value is None:
                        continue
                    value = default_value
                properties[parameter_name] = value
        return properties

    @staticmethod
    def dumps(payload: dict) -> str | dict:
        """
        Encode payload as base64-encoded JSON string.

        This method converts a dictionary payload to a base64-encoded JSON string
        for transmission through the Link system.

        :param payload: Dictionary payload to encode
        :return: Base64-encoded JSON string or original payload if empty
        """
        if payload:
            return b64encode(json.dumps(payload, ensure_ascii=True).encode()).decode()
        return payload

    async def run(
        self, action_input: dict, business_input: dict, span: Span, **kwargs: Any
    ) -> Dict[str, Any]:
        """
        Execute the tool operation through the Link system.

        This method assembles the request parameters, makes an HTTP call to the
        Link system, and processes the response. It handles parameter encoding,
        request construction, and response parsing.

        :param action_input: Action-specific input parameters
        :param business_input: Business-specific input parameters
        :param span: Tracing span for monitoring execution
        :param kwargs: Additional keyword arguments
        :return: Parsed response data from the tool execution
        :raises CustomException: When tool execution fails or connection errors occur
        """

        with span.start("run_link_tool") as link_tool_span:
            event_log_node_trace = kwargs.get("event_log_node_trace")
            link_tool_span.add_info_events(
                {"schema": json.dumps(self.method_schema, ensure_ascii=False)}
            )

            start_time = time.time() * 1000
            # Extract request body schema from OpenAPI method schema
            body_schema = (
                self.method_schema.get("requestBody", {})
                .get("content", {})
                .get("application/json", {})
                .get("schema", {})
            )
            link_tool_span.add_info_events(
                {"plugin_node_link_get_cost_time": f"{time.time() * 1000 - start_time}"}
            )

            # Assemble HTTP parameters and request body
            _header, _query, _path = self.assemble_parameters(
                action_input, business_input
            )
            _body = self.assemble_body(body_schema, action_input, business_input)

            # Construct Link system request payload
            run_link_payload: Dict[str, Any] = {
                "header": {},
                "parameter": {},
                "payload": {"message": {}},
            }
            run_link_payload["header"]["app_id"] = self.app_id
            run_link_payload["parameter"]["tool_id"] = self.tool_id
            run_link_payload["parameter"]["operation_id"] = self.operation_id
            run_link_payload["parameter"]["version"] = self.version

            # Encode parameters for transmission
            callback_payload: Dict[str, Any] = {}
            header = self.dumps(_header)
            query = self.dumps(_query)
            path = self.dumps(_path)
            body = self.dumps(_body)
            # Add encoded parameters to payload if they exist
            if header:
                run_link_payload["payload"]["message"]["header"] = header
                callback_payload["header"] = _header
            if query:
                run_link_payload["payload"]["message"]["query"] = query
                callback_payload["query"] = _query
            if body:
                run_link_payload["payload"]["message"]["body"] = body
                callback_payload["body"] = _body
            if path:
                run_link_payload["payload"]["message"]["path"] = path
                callback_payload["path"] = _path

            tool_input = json.dumps(callback_payload, ensure_ascii=False)

            # Log request information for debugging and monitoring
            link_tool_span.add_info_events({"input": tool_input})
            link_tool_span.add_info_events(
                {"link_input": json.dumps(run_link_payload, ensure_ascii=False)}
            )

            if event_log_node_trace:
                event_log_node_trace.append_config_data(
                    {
                        "tool_input": tool_input,
                        "url": self.run_url,
                        "link_req_payload": json.dumps(
                            run_link_payload, ensure_ascii=False
                        ),
                    }
                )

            # Execute HTTP request to Link system
            try:
                from aiohttp import ClientSession

                # Make asynchronous HTTP request to Link system
                async with ClientSession() as session:
                    start_time = time.time() * 1000
                    async with session.post(
                        self.run_url, json=run_link_payload
                    ) as response:
                        link_response = await response.json()
                        # Log response timing and content
                        link_tool_span.add_info_events(
                            {
                                "plugin_node_link_post_cost_time": f"{time.time() * 1000 - start_time}"
                            }
                        )
                        link_tool_span.add_info_events(
                            {
                                "link_response": json.dumps(
                                    link_response, ensure_ascii=False
                                )
                            }
                        )
            except requests.ConnectionError as e:
                # Handle connection errors
                raise CustomException(
                    CodeEnum.SPARK_LINK_CONNECTION_ERROR,
                    err_msg="Tool request failed, connection error",
                    cause_error="Tool request failed, connection error",
                ) from e
            except Exception as e:
                # Handle other exceptions
                raise e

            link_tool_span.add_info_events(
                {"link_response": json.dumps(link_response, ensure_ascii=False)}
            )

            # Process response and handle errors
            code = link_response["header"]["code"]
            message = link_response["header"]["message"]

            if code != 0:
                # Handle tool execution errors
                raise CustomException(
                    err_code=CodeConvert.sparkLinkCode(code),
                    err_msg=message,
                    cause_error=json.dumps(link_response, ensure_ascii=False),
                )
            else:
                # Extract and parse successful response
                tool_response_text = link_response["payload"]["text"]["text"]
                return json.loads(tool_response_text)


class Link:
    """
    Link system client for managing and executing plugin tools.

    This class handles communication with the Link system to retrieve tool schemas
    and manage tool execution. It parses OpenAPI schemas and creates Tool instances
    for each available operation.
    """

    const_headers = {"Content-Type": "application/json"}

    def __init__(
        self,
        app_id: str,
        tool_ids: list[str],
        get_url: str,
        run_url: str,
        version: str = "V1.0",
    ):
        """
        Initialize Link client instance.

        :param app_id: Application identifier
        :param tool_ids: List of tool identifiers to manage
        :param get_url: URL for retrieving tool schema information
        :param run_url: URL for executing tool operations
        :param version: Tool version (default: "V1.0")
        """
        self.app_id = app_id
        self.tool_ids = tool_ids
        self.get_url = get_url
        self.run_url = run_url
        self.version = version
        # Retrieve OpenAPI schema list from Spark Link system
        self.open_api_schema_list = self.tool_schema_list()
        self.tools: List[Tool] = []  # List of Tool instances
        # Parse schemas and create Tool instances
        self.parse_react_schema_list()

    def tool_schema_list(self) -> List[Dict[str, Any]]:
        """
        Query tool schema list from Spark Link subsystem.

        This method makes an HTTP request to retrieve the OpenAPI schemas
        for the specified tools from the Link system.

        :return: List of tool schema dictionaries
        """
        params = {
            "tool_ids": self.tool_ids,
            "versions": [self.version],
            "app_id": self.app_id,
        }
        response_json = requests.get(
            self.get_url, headers=self.const_headers, params=params
        ).json()
        if response_json.get("code", CodeEnum.SPARK_LINK_ACTION_ERROR.code) != 0:
            # TODO: Add logging for error cases
            return []
        return response_json.get("data", {}).get("tools", [])

    @staticmethod
    def parse_request_query_schema(
        query_schema: list,
    ) -> Tuple[Dict[str, Dict[str, str]], set]:
        """
        Parse query parameters from OpenAPI schema.

        This method extracts query parameters from the OpenAPI parameter schema
        and identifies which parameters are required.

        :param query_schema: List of parameter definitions from OpenAPI schema
        :return: Tuple containing (query_parameters_dict, required_parameters_set)
        """
        query_parameters = {}
        query_required = set()
        for parameter in query_schema:
            parameter_name = parameter.get("name")
            parameter_description = parameter.get("description")
            parameter_type = parameter.get("schema", {}).get("type")
            parameter_in = parameter.get("in")
            parameter_required = parameter.get("required")

            if parameter_in == "query":
                query_parameters[parameter_name] = {
                    "description": parameter_description,
                    "type": parameter_type,
                }
                if parameter_required:
                    query_required.add(parameter_name)
        return query_parameters, query_required

    def recursive_parse_request_body_schema(
        self, body_schema: dict, properties: dict, required_set: set
    ) -> None:
        """
        Recursively parse request body schema.

        This method processes the OpenAPI request body schema recursively to extract
        all parameter definitions and required field information.

        :param body_schema: OpenAPI request body schema
        :param properties: Dictionary to store parsed properties
        :param required_set: Set to store required field names
        """
        request_body_properties = body_schema.get("properties", {})
        for parameter_name, parameter_detail in request_body_properties.items():
            parameter_description = parameter_detail.get("description", "")
            parameter_type = parameter_detail.get("type")
            if parameter_type == "object":
                # Recursively process nested objects
                self.recursive_parse_request_body_schema(
                    parameter_detail, properties, required_set
                )
            else:
                properties[parameter_name] = {
                    "description": parameter_description,
                    "type": parameter_type,
                }
        # Add top-level required fields
        request_body_required = body_schema.get("required", [])
        required_set.update(request_body_required)

    def parse_react_schema_list(self) -> None:
        """
        Parse OpenAPI schemas and generate Tool instances for ReAct framework.

        This method processes the retrieved OpenAPI schemas to create Tool instances
        for each available operation. It handles both query parameters and request
        body parameters, merging them into a unified parameter structure.
        """
        for tool_schema in self.open_api_schema_list:
            tool_id = tool_schema.get("id")
            if tool_id is None:
                raise CustomException(
                    CodeEnum.SPARK_LINK_TOOL_NOT_EXIST_ERROR,
                    err_msg="Tool ID is empty",
                    cause_error=json.dumps(tool_schema, ensure_ascii=False),
                )
            tool_schema = json.loads(tool_schema.get("schema", "{}"))
            # Process each path and method in the OpenAPI schema
            for path, path_schema in tool_schema.get("paths", {}).items():
                for method, method_schema in path_schema.items():
                    action_name = method_schema.get(
                        "operationId", ""
                    )  # Tool operation name
                    # Parse query parameters
                    query_schema = method_schema.get("parameters", [])
                    query_parameters, query_required = self.parse_request_query_schema(
                        query_schema
                    )
                    # Parse request body (currently only supports application/json format)
                    request_body_schema = (
                        method_schema.get("requestBody", {})
                        .get("content", {})
                        .get("application/json", {})
                        .get("schema", {})
                    )
                    body_parameters: Dict[str, Any] = {}
                    body_required: Set[str] = set()
                    self.recursive_parse_request_body_schema(
                        request_body_schema, body_parameters, body_required
                    )
                    # Merge body and query parameters
                    parameters: dict[str, dict] = dict(
                        **query_parameters, **body_parameters
                    )
                    # Create Tool execution instance
                    tool = Tool(
                        app_id=self.app_id,
                        tool_id=tool_id,
                        operation_id=action_name,
                        method_schema=method_schema,
                        parameters=parameters,
                        get_url=self.get_url,
                        run_url=self.run_url,
                        version=self.version,
                    )
                    self.tools.append(tool)
