import asyncio
import json
import time
from base64 import b64encode
from typing import Any, Optional

import aiohttp
from pydantic import BaseModel, Field

from common_imports import Span
from exceptions.plugin_exc import GetToolSchemaExc, RunToolExc
from infra import agent_config
from service.plugin.base import BasePlugin, PluginResponse


class LinkPluginRunner(BaseModel):
    app_id: str
    uid: str
    tool_id: str
    version: str
    operation_id: str
    method_schema: dict[str, Any]

    def assemble_parameters(
        self, action_input: dict[str, Any], business_input: dict[str, Any]
    ) -> tuple[dict[str, Any], dict[str, Any]]:
        header: dict[str, Any] = {}
        query: dict[str, Any] = {}
        parameters_schema = self.method_schema.get("parameters", [])
        for parameter in parameters_schema:
            if parameter["in"] == "header":
                self.update_params(header, parameter, action_input, business_input)
            elif parameter["in"] == "query":
                self.update_params(query, parameter, action_input, business_input)
        return header, query

    @staticmethod
    def update_params(
        params: dict[str, Any],
        header_parameter: dict[str, Any],
        action_input: dict[str, Any],
        business_input: dict[str, Any],
    ) -> None:
        """Assemble header"""
        x_from = header_parameter.get("schema", {}).get("x-from")
        name = header_parameter.get("name", "unknown_field")
        value = header_parameter.get("schema", {}).get("default")
        if "x-display" in header_parameter.get("schema", {}):
            x_display = header_parameter.get("schema", {}).get("x-display")
            if x_display:  # Model recognition
                value = action_input.get(name, value)
            # else: # Business passthrough
            #     value = business_input.get(name, value)
        else:
            if x_from == 0:  # Model recognition
                value = action_input.get(name, value)
            elif x_from == 1:  # Business passthrough
                value = business_input.get(name, value)
        params[name] = value

    def assemble_body(
        self,
        body_schema: Any,
        action_input: dict[str, Any],
        business_input: dict[str, Any],
    ) -> dict[str, Any]:
        properties = {}
        body_properties = body_schema.get("properties", {})
        for parameter_name, parameter_detail in body_properties.items():
            parameter_type = parameter_detail.get("type")
            if parameter_type == "object":
                # recursive
                _properties = self.assemble_body(
                    parameter_detail, action_input, business_input
                )
                properties[parameter_name] = _properties
            else:
                x_from = parameter_detail.get("x-from")
                value = parameter_detail.get("default")
                if "x-display" in parameter_detail:
                    x_display = parameter_detail.get("x-display")
                    if x_display:
                        value = action_input.get(parameter_name, value)
                    # else:
                    #     value = business_input.get(parameter_name, value)
                else:
                    if x_from == 0:
                        value = action_input.get(parameter_name, value)
                    elif x_from == 1:
                        value = business_input.get(parameter_name, value)
                    else:
                        value = action_input.get(parameter_name, value)
                properties[parameter_name] = value
        return properties

    @staticmethod
    def dumps(payload: dict[str, Any]) -> str:
        if payload:
            return b64encode(json.dumps(payload, ensure_ascii=True).encode()).decode()
        return ""

    async def run(self, action_input: dict[str, Any], span: Span) -> PluginResponse:
        """Call link"""
        with span.start("Run") as sp:
            start_time = int(round(time.time() * 1000))
            body_schema = (
                self.method_schema.get("requestBody", {})
                .get("content", {})
                .get("application/json", {})
                .get("schema", {})
            )
            _header, _query = self.assemble_parameters(action_input, {})
            _body = self.assemble_body(body_schema, action_input, {})

            run_link_payload: dict[str, Any] = {
                "header": {},
                "parameter": {},
                "payload": {"message": {}},
            }
            run_link_payload["header"]["app_id"] = self.app_id
            run_link_payload["header"]["uid"] = self.uid
            run_link_payload["parameter"]["tool_id"] = self.tool_id
            run_link_payload["parameter"]["operation_id"] = self.operation_id
            run_link_payload["parameter"]["version"] = self.version

            callback_payload: dict[str, Any] = {}
            header = self.dumps(_header)
            query = self.dumps(_query)
            body = self.dumps(_body)
            if header:
                run_link_payload["payload"]["message"]["header"] = header
                callback_payload["header"] = _header
            if query:
                run_link_payload["payload"]["message"]["query"] = query
                callback_payload["query"] = _query
            if body:
                run_link_payload["payload"]["message"]["body"] = body
                callback_payload["body"] = _body
            sp.add_info_events(
                attributes={
                    "link-plugin-run-inputs": json.dumps(
                        run_link_payload, ensure_ascii=False
                    )
                }
            )
            # Finished parsing parameters, start calling link
            result: dict[str, Any] = {}
            try:
                timeout = aiohttp.ClientTimeout(total=40)
                async with aiohttp.ClientSession() as session:
                    async with session.post(
                        agent_config.RUN_LINK_URL,
                        data=json.dumps(run_link_payload),
                        timeout=timeout,
                        headers={"Content-Type": "application/json"},
                    ) as response:
                        response.raise_for_status()
                        if response.status == 200:
                            result = await response.json()
                            sp.add_info_events(
                                attributes={
                                    "link-plugin-run-outputs": json.dumps(
                                        result, ensure_ascii=False
                                    )
                                }
                            )
                        else:
                            sp.add_info_events(
                                attributes={
                                    "link-plugin-run-outputs": (
                                        f"response code is {response.status}"
                                    )
                                }
                            )
                            raise RunToolExc
            except asyncio.TimeoutError as e:
                raise RunToolExc from e

            end_time = int(round(time.time() * 1000))
            plugin_response = PluginResponse(
                code=result.get("header", {}).get("code", -1),
                sid=result.get("header", {}).get("sid", ""),
                start_time=start_time,
                end_time=end_time,
                result=result,
                log=[
                    {
                        "name": self.operation_id,
                        "input": callback_payload,
                        "output": result,
                    }
                ],
            )

            return plugin_response


class LinkPlugin(BasePlugin):
    tool_id: str


class LinkPluginFactory(BaseModel):
    app_id: str
    uid: str
    tool_ids: list[str | dict[str, Any]]

    const_headers: dict[str, str] = Field(default={"Content-Type": "application/json"})

    async def gen(self, span: Span) -> list[LinkPlugin]:
        return await self.parse_react_schema_list(span)

    async def run(
        self, _operation_id: str, _action_input: dict[str, Any], _span: Span
    ) -> Optional[PluginResponse]:
        # This method appears to be incomplete in the original, return None
        return None

    async def tool_schema_list(self, span: Span) -> list[dict[str, Any]]:
        """Query protocol list from spark link subsystem"""
        with span.start("ToolSchemaList") as sp:
            if not self.tool_ids:
                return []

            url = agent_config.VERSIONS_LINK_URL + "?" + f"app_id={self.app_id}"

            for tool_id in self.tool_ids:
                if isinstance(tool_id, str):
                    url += "&tool_ids=" + tool_id + "&versions=V1.0"
                elif isinstance(tool_id, dict):
                    tl_id = tool_id.get("tool_id", "")
                    tl_version = tool_id.get("version", "")
                    url += "&tool_ids=" + tl_id + "&versions=" + tl_version
            sp.add_info_events(attributes={"link-plugin-tool-schema-list-inputs": url})
            async with aiohttp.ClientSession() as session:
                async with session.get(url) as response:
                    response.raise_for_status()
                    if response.status == 200:
                        result = await response.json()
                        sp.add_info_events(
                            attributes={
                                "link-plugin-tool-schema-list-outputs": (
                                    json.dumps(result, ensure_ascii=False)
                                )
                            }
                        )
                        print(result)
                        if result.get("code") != 0:
                            raise GetToolSchemaExc
                        tools_data = result.get("data", {}).get("tools", [])
                        return tools_data if isinstance(tools_data, list) else []

                    sp.add_info_events(
                        attributes={
                            "link-plugin-tool-schema-list-outputs": (
                                f"response code is {response.status}"
                            )
                        }
                    )
                    raise GetToolSchemaExc

    @staticmethod
    def parse_request_query_schema(
        query_schema: list[dict[str, Any]],
    ) -> tuple[dict[str, dict[str, Any]], set[str]]:
        """Parse parameters"""

        query_parameters: dict[str, dict[str, Any]] = {}
        query_required: set[str] = set()
        for parameter in query_schema:

            parameter_name = parameter.get("name")
            if parameter_name is None:
                continue

            parameter_description = parameter.get("description")
            parameter_type = parameter.get("schema", {}).get("type")
            parameter_in = parameter.get("in")
            parameter_required = parameter.get("required")
            parameter_x_from = parameter.get("schema", {}).get("x-from")
            if "x-display" in parameter.get("schema", {}):
                parameter_x_display = parameter.get("schema", {}).get("x-display")
                if parameter_x_display:  # Model recognition
                    if parameter_in == "query":
                        query_parameters[parameter_name] = {
                            "description": parameter_description,
                            "type": parameter_type,
                        }
                        if parameter_required:
                            query_required.add(parameter_name)
            else:
                if parameter_x_from == 0:  # Model recognition
                    if parameter_in == "query":
                        query_parameters[parameter_name] = {
                            "description": parameter_description,
                            "type": parameter_type,
                        }
                        if parameter_required:
                            query_required.add(parameter_name)
        return query_parameters, query_required

    def recursive_parse_request_body_schema(
        self,
        body_schema: dict[str, Any],
        properties: dict[str, dict[str, Any]],
        required_set: set[str],
    ) -> None:
        """Recursively parse body"""
        request_body_properties = body_schema.get("properties", {})
        for parameter_name, parameter_detail in request_body_properties.items():
            parameter_description = parameter_detail.get("description", "")
            parameter_type = parameter_detail.get("type")
            parameter_x_from = parameter_detail.get("x-from")
            if parameter_type == "object":
                # recursive
                self.recursive_parse_request_body_schema(
                    parameter_detail, properties, required_set
                )
            else:
                if "x-display" in parameter_detail:
                    parameter_x_display = parameter_detail.get("x-display")
                    if parameter_x_display:
                        properties[parameter_name] = {
                            "description": parameter_description,
                            "type": parameter_type,
                        }
                else:
                    if parameter_x_from == 0:
                        properties[parameter_name] = {
                            "description": parameter_description,
                            "type": parameter_type,
                        }
        # Outermost required values
        request_body_required = body_schema.get("required", [])
        required_set.update(request_body_required)

    async def parse_react_schema_list(self, span: Span) -> list[LinkPlugin]:
        """Generate tools and tool_names for ReAct"""
        with span.start("ParseReactSchemaList") as sp:
            tools: list[LinkPlugin] = []

            for tool_schema in await self.tool_schema_list(sp):
                tool_id = tool_schema.get("id")
                version = tool_schema.get("version")
                if tool_id is None or version is None:
                    continue

                tool_schema_data = json.loads(tool_schema.get("schema", "{}"))
                for _, path_schema in tool_schema_data.get("paths", {}).items():
                    for _, method_schema in path_schema.items():
                        action_name = method_schema.get("operationId", "")  # Tool name
                        action_description = method_schema.get(
                            "description", ""
                        )  # Tool description

                        # Parse query
                        query_schema = method_schema.get("parameters", [])
                        query_parameters, query_required = (
                            self.parse_request_query_schema(query_schema)
                        )

                        # Parse body, currently only supports application/json format
                        request_body_schema = (
                            method_schema.get("requestBody", {})
                            .get("content", {})
                            .get("application/json", {})
                            .get("schema", {})
                        )
                        body_parameters: dict[str, dict[str, Any]] = {}
                        body_required: set[str] = set()
                        self.recursive_parse_request_body_schema(
                            request_body_schema, body_parameters, body_required
                        )

                        # Remove nested keys
                        delete_required_keys = []
                        for k in body_required:
                            if k not in body_parameters:
                                delete_required_keys.append(k)
                        for k in delete_required_keys:
                            body_required.discard(k)

                        # Merge body and query
                        parameters: dict[str, dict[str, Any]] = {
                            **query_parameters,
                            **body_parameters,
                        }
                        required = [*query_required, *body_required]

                        property_template = json.dumps(
                            {
                                "type": "object",
                                "properties": parameters,
                                "required": required,
                            },
                            ensure_ascii=False,
                        )
                        schema_template = (
                            f"tool_name:{action_name}, "
                            f"tool_description:{action_description}, "
                            f"tool_parameters:{property_template}"
                        )

                        # Create tool execution object
                        tool = LinkPlugin(
                            tool_id=tool_id,
                            name=action_name,
                            description=action_description,
                            schema_template=schema_template,
                            typ="link",
                            run=LinkPluginRunner(
                                app_id=self.app_id,
                                uid=self.uid,
                                tool_id=tool_id,
                                version=version,
                                operation_id=action_name,
                                method_schema=method_schema,
                            ).run,
                        )
                        tools.append(tool)
            return tools
