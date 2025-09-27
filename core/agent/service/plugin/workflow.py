import asyncio
import json
import time
from dataclasses import dataclass
from typing import Any, AsyncIterator

import aiohttp
import httpx
from openai import AsyncOpenAI
from pydantic import BaseModel, Field

from common_imports import Span
from exceptions.plugin_exc import RunWorkflowExc
from infra import agent_config
from service.plugin.base import BasePlugin, PluginResponse


@dataclass
class ResponseContext:
    """Response context parameters"""

    code: int
    sid: str
    start_time: int
    end_time: int
    action_input: dict


class WorkflowPluginRunner(BaseModel):
    app_id: str
    uid: str
    flow_id: str
    stream: bool = Field(default=True)

    def _build_request_params(self, action_input: dict) -> dict:
        """Build request parameters"""
        return {
            "model": "",
            "messages": [],
            "stream": True,
            "extra_body": {
                "flow_id": self.flow_id,
                "uid": self.uid,
                "parameters": action_input,
                "extra_body": {"bot_id": "workflow", "caller": "agent"},
            },
            "extra_headers": {"X-consumer-username": self.app_id},
        }

    def _create_error_response(
        self, ctx: ResponseContext, chunk_data: dict
    ) -> PluginResponse:
        """Create error response"""
        return PluginResponse(
            code=ctx.code,
            sid=ctx.sid,
            start_time=ctx.start_time,
            end_time=ctx.end_time,
            result=chunk_data,
            log=[
                {
                    "name": self.flow_id,
                    "input": ctx.action_input,
                    "output": chunk_data,
                    "sid": ctx.sid,
                }
            ],
        )

    def _create_success_response(
        self, ctx: ResponseContext, content: str, reasoning_content: str
    ) -> PluginResponse:
        """Create success response"""
        return PluginResponse(
            code=ctx.code,
            sid=ctx.sid,
            start_time=ctx.start_time,
            end_time=ctx.end_time,
            result={
                "content": content,
                "reasoning_content": reasoning_content,
            },
            log=[
                {
                    "name": self.flow_id,
                    "input": ctx.action_input,
                    "content": content,
                    "reasoning_content": reasoning_content,
                    "sid": ctx.sid,
                }
            ],
        )

    async def run(
        self, action_input: dict, span: Span
    ) -> AsyncIterator[PluginResponse]:
        with span.start("Run") as sp:
            start_time = int(round(time.time() * 1000))
            params = self._build_request_params(action_input)

            sp.add_info_events(
                attributes={
                    "workflow-plugin-run-inputs": json.dumps(params, ensure_ascii=False)
                }
            )

            flow_client = AsyncOpenAI(
                base_url=agent_config.WORKFLOW_SSE_BASE_URL, api_key="no_need"
            )

            try:
                response = await flow_client.chat.completions.create(
                    **params, timeout=40
                )
                async for chunk in response:
                    chunk_data = chunk.model_dump()
                    sp.add_info_events(
                        attributes={
                            "workflow-plugin-run-outputs": json.dumps(
                                chunk_data, ensure_ascii=False
                            )
                        }
                    )

                    ctx = ResponseContext(
                        code=chunk_data.get("code"),
                        sid=chunk_data.get("id"),
                        start_time=start_time,
                        end_time=int(round(time.time() * 1000)),
                        action_input=action_input,
                    )

                    if ctx.code != 0:
                        yield self._create_error_response(ctx, chunk_data)
                    elif not chunk.choices:
                        continue
                    else:
                        content = chunk.choices[0].delta.content
                        reasoning_content = (
                            chunk.choices[0]
                            .delta.to_dict()
                            .get("reasoning_content", "")
                        )
                        yield self._create_success_response(
                            ctx, content, reasoning_content
                        )
            except httpx.TimeoutException as e:
                raise RunWorkflowExc from e


class WorkflowPlugin(BasePlugin):
    flow_id: str


class WorkflowPluginFactory(BaseModel):
    app_id: str
    uid: str
    workflow_ids: list

    async def gen(self, span: Span) -> list[WorkflowPlugin]:
        schema_list = await self.query_workflows_schema_list(span)
        plugins = []
        for schema in schema_list:
            plugin = await self.create_workflow_plugin(schema)
            plugins.append(plugin)
        return plugins

    @staticmethod
    async def do_query_workflow_schema(workflow_id: str, span: Span) -> dict[str, Any]:
        with span.start("DoQueryWorkflowsSchema") as sp:
            sp.add_info_events(
                attributes={
                    "workflow-plugin-do-query-workflow-schema-inputs": json.dumps(
                        {"flow_id": workflow_id}, ensure_ascii=False
                    )
                }
            )
            async with aiohttp.ClientSession() as session:
                async with session.post(
                    agent_config.GET_WORKFLOWS_URL, json={"flow_id": workflow_id}
                ) as response:
                    response.raise_for_status()
                    result = await response.json()
                    sp.add_info_events(
                        attributes={
                            "workflow-plugin-do-query-workflow-schema-outputs": (
                                json.dumps(result, ensure_ascii=False)
                            )
                        }
                    )
                    return dict(result)

    async def query_workflows_schema_list(self, span: Span) -> list:
        with span.start("QueryWorkflowsSchemaList") as sp:
            query_tasks = [
                self.do_query_workflow_schema(flow_id, sp)
                for flow_id in self.workflow_ids
            ]
            results = await asyncio.gather(*query_tasks)
            return results

    async def create_workflow_plugin(self, workflow_schema: dict) -> WorkflowPlugin:
        flow_id = workflow_schema.get("data", {}).get("data", {}).get("id", "")
        flow_name = workflow_schema.get("data", {}).get("data", {}).get("name", "")
        flow_description = (
            workflow_schema.get("data", {}).get("data", {}).get("description", "")
        )
        nodes = (
            workflow_schema.get("data", {})
            .get("data", {})
            .get("data", {})
            .get("nodes", [])
        )

        for node in nodes:
            if node.get("id", "").startswith("node-start::"):
                react_parameters = {}
                required_list = []
                for output in node.get("data", {}).get("outputs", []):
                    react_parameters[output.get("name")] = output.get("schema", {})
                    if output.get("required", False):
                        required_list.append(output.get("name"))
                t_p = json.dumps(
                    {
                        "type": "object",
                        "properties": react_parameters,
                        "required": required_list,
                    },
                    ensure_ascii=False,
                )
                react_plugin_prompt = (
                    f"tool_name:{flow_name}, tool_description:{flow_description}, "
                    f"tool_parameters:{t_p}"
                )

                workflow_plugin = WorkflowPlugin(
                    flow_id=flow_id,
                    name=flow_name,
                    description=flow_description,
                    schema_template=react_plugin_prompt,
                    typ="workflow",
                    run=WorkflowPluginRunner(
                        app_id=self.app_id, uid=self.uid, flow_id=flow_id
                    ).run,
                )
                return workflow_plugin

        # If no valid workflow node found, return default plugin
        return WorkflowPlugin(
            flow_id=flow_id,
            name=flow_name or "unknown",
            description=flow_description or "unknown workflow",
            schema_template=(
                "tool_name:unknown, tool_description:unknown workflow, "
                "tool_parameters:{}"
            ),
            typ="workflow",
            run=WorkflowPluginRunner(
                app_id=self.app_id, uid=self.uid, flow_id=flow_id
            ).run,
        )
