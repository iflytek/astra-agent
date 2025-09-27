"""Enterprise extension service module for MCP (Model Context Protocol) management.

This module provides functionality for registering and managing MCP tools
in the enterprise environment, including validation, database operations,
and telemetry tracking.
"""

import json
import os
import time

from common.otlp.log_trace.node_trace_log import NodeTraceLog, Status
from common.otlp.metrics.meter import Meter
from common.otlp.trace.span import Span
from common.service import get_kafka_producer_service
from loguru import logger
from plugin.link.api.schemas.community.deprecated.management_schema import (
    ToolManagerResponse,
)
from plugin.link.api.schemas.enterprise.extension_schema import (
    MCPManagerRequest,
    MCPManagerResponse,
)
from plugin.link.consts import const
from plugin.link.domain.models.manager import get_db_engine
from plugin.link.infra.tool_crud.process import ToolCrudOperation
from plugin.link.utils.errors.code import ErrCode
from plugin.link.utils.json_schemas.read_json_schemas import get_mcp_register_schema
from plugin.link.utils.json_schemas.schema_validate import api_validate
from plugin.link.utils.snowflake.gen_snowflake import gen_id


def register_mcp(mcp_info: MCPManagerRequest):
    """Register a new MCP (Model Context Protocol) tool in the system.

    Validates the MCP registration request, generates a unique tool ID,
    and persists the MCP tool information to the database with proper
    telemetry tracking and error handling.

    Args:
        mcp_info (MCPManagerRequest): The MCP registration request containing
            tool metadata including name, description, schema, and server URL.

    Returns:
        Union[MCPManagerResponse, ToolManagerResponse]: Response containing
            the registration result with tool ID and status information.
    """
    try:
        run_params_list = mcp_info.model_dump()
        app_id = run_params_list.get("app_id", os.getenv(const.DEFAULT_APPID_KEY))
        flow_id = run_params_list.get("flow_id", "")
        span = Span(app_id=app_id, uid=flow_id)
        with span.start(func_name="register_mcp") as span_context:
            # Generate tool ID
            logger.info(
                {
                    "manager api, register_mcp router usr_input": json.dumps(
                        run_params_list, ensure_ascii=False
                    )
                }
            )
            span_context.add_info_events(
                {"usr_input": json.dumps(run_params_list, ensure_ascii=False)}
            )

            node_trace = NodeTraceLog(
                service_id="",
                sid=span_context.sid,
                app_id=span_context.app_id,
                uid=span_context.uid,
                chat_id=span_context.sid,
                sub="spark-link",
                caller="",
                log_caller="mcp",
                question=json.dumps(run_params_list, ensure_ascii=False),
            )
            m = Meter(app_id=span_context.app_id, func="register_mcp")
            validate_err = api_validate(get_mcp_register_schema(), run_params_list)
            if validate_err:
                if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
                    m.in_error_count(ErrCode.JSON_PROTOCOL_PARSER_ERR.code)
                    node_trace.answer = validate_err
                    node_trace.status = Status(
                        code=ErrCode.JSON_PROTOCOL_PARSER_ERR.code,
                        message=validate_err,
                    )
                    kafka_service = get_kafka_producer_service()
                    node_trace.start_time = int(round(time.time() * 1000))
                    kafka_service.send(os.getenv(const.KAFKA_TOPIC_KEY), node_trace.to_json())
                return MCPManagerResponse(
                    code=ErrCode.JSON_PROTOCOL_PARSER_ERR.code,
                    message=validate_err,
                    sid=span_context.sid,
                    data={},
                )

            new_id = f"{hex(gen_id())}"
            tool_type = run_params_list.get("type", "")
            if flow_id:
                tool_id = f"mcp@{tool_type}{flow_id}"
            else:
                tool_id = f"mcp@{tool_type}{new_id[2:]}"
            schema = run_params_list.get("mcp_schema", "")
            mcp_name = run_params_list.get("name", "")
            mcp_description = run_params_list.get("description", "")
            mcp_server_url = run_params_list.get("mcp_server_url", "")
            tool_info = {
                "app_id": app_id,
                "tool_id": tool_id,
                "schema": schema,
                "name": mcp_name,
                "description": mcp_description,
                "mcp_server_url": mcp_server_url,
                "version": const.DEF_VER,
                "is_deleted": const.DEF_DEL,
            }
            span_context.set_attributes(
                attributes={"mcp": json.dumps(tool_info, ensure_ascii=False)}
            )
            crud_inst = ToolCrudOperation(get_db_engine())
            crud_inst.add_mcp(tool_info)
            resp_data = {"name": mcp_name, "id": tool_id}
            if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
                m.in_success_count()
                node_trace.answer = json.dumps(resp_data, ensure_ascii=False)
                node_trace.service_id = str(tool_id)
                node_trace.status = Status(
                    code=ErrCode.SUCCESSES.code,
                    message=ErrCode.SUCCESSES.msg,
                )
                kafka_service = get_kafka_producer_service()
                node_trace.start_time = int(round(time.time() * 1000))
                kafka_service.send(os.getenv(const.KAFKA_TOPIC_KEY), node_trace.to_json())
            return ToolManagerResponse(
                code=ErrCode.SUCCESSES.code,
                message=ErrCode.SUCCESSES.msg,
                sid=span_context.sid,
                data=resp_data,
            )
    except Exception as err:
        logger.error(f"failed to create tools, reason {err}")
        if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
            m.in_error_count(ErrCode.COMMON_ERR.code)
            node_trace.answer = str(err)
            node_trace.status = Status(
                code=ErrCode.COMMON_ERR.code,
                message=str(err),
            )
            kafka_service = get_kafka_producer_service()
            node_trace.start_time = int(round(time.time() * 1000))
            kafka_service.send(os.getenv(const.KAFKA_TOPIC_KEY), node_trace.to_json())
        return ToolManagerResponse(
            code=ErrCode.COMMON_ERR.code,
            message=str(err),
            sid=span_context.sid,
            data={},
        )
