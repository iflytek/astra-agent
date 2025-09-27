"""
HTTP management server module for community tools.

This module provides HTTP endpoints for managing community tools in the Stellar Agent
platform. It includes functionality for creating, reading, updating, and deleting tool
versions with proper validation, authentication, and observability support through
OpenTelemetry tracing.

The module handles:
- Tool version lifecycle management (CRUD operations)
- OpenAPI schema validation for tool definitions
- Application ID validation and authentication
- Distributed tracing and metrics collection
- Error handling and standardized response formatting
"""

import json
import os
import re
import time

from common.otlp.log_trace.node_trace_log import NodeTraceLog, Status
from common.otlp.metrics.meter import Meter
from common.otlp.trace.span import Span
from common.service import get_kafka_producer_service
from fastapi import Query
from loguru import logger
from opentelemetry.trace import Status as OTelStatus
from opentelemetry.trace import StatusCode
from plugin.link.api.schemas.community.tools.http.management_schema import (
    ToolCreateRequest,
    ToolManagerResponse,
    ToolUpdateRequest,
)
from plugin.link.consts import const
from plugin.link.domain.models.manager import get_db_engine
from plugin.link.exceptions.sparklink_exceptions import SparkLinkBaseException
from plugin.link.infra.tool_crud.process import ToolCrudOperation
from plugin.link.utils.errors.code import ErrCode
from plugin.link.utils.json_schemas.read_json_schemas import (
    get_create_tool_schema,
    get_update_tool_schema,
)
from plugin.link.utils.json_schemas.schema_validate import api_validate
from plugin.link.utils.open_api_schema.schema_validate import OpenapiSchemaValidator
from plugin.link.utils.snowflake.gen_snowflake import gen_id
from plugin.link.utils.uid.generate_uid import new_uid


def extract_management_params(run_params_list):
    """Extract common parameters from management requests."""
    app_id = (
        run_params_list.get("header", {}).get("app_id")
        if run_params_list.get("header", {}).get("app_id")
        else os.getenv(const.DEFAULT_APPID_KEY)
    )
    uid = (
        run_params_list.get("header", {}).get("uid")
        if run_params_list.get("header", {}).get("uid")
        else new_uid()
    )
    caller = (
        run_params_list.get("header", {}).get("caller")
        if run_params_list.get("header", {}).get("caller")
        else ""
    )
    tool_type = (
        run_params_list.get("header", {}).get("tool_type")
        if run_params_list.get("header", {}).get("tool_type")
        else ""
    )
    return app_id, uid, caller, tool_type


def setup_span_and_trace_mgmt(
    run_params_list, app_id, uid, caller, tool_type, service_id=""
):
    """Setup span and trace for management operations."""
    span = Span(app_id=app_id, uid=uid)
    sid = run_params_list.get("header", {}).get("sid")
    if sid:
        span.sid = sid

    node_trace = NodeTraceLog(
        service_id=service_id,
        sid=sid or "",
        app_id=app_id,
        uid=uid,
        chat_id=sid or "",
        sub="spark-link",
        caller=caller,
        log_caller=tool_type,
        question=json.dumps(run_params_list, ensure_ascii=False),
    )
    return span, node_trace


def send_telemetry_mgmt(node_trace):
    """Send telemetry data to Kafka."""
    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        kafka_service = get_kafka_producer_service()
        node_trace.start_time = int(round(time.time() * 1000))
        kafka_service.send(
            os.getenv(const.KAFKA_TOPIC_KEY),
            node_trace.to_json()
        )


def setup_logging_and_metrics_mgmt(span_context, run_params_list, func_name):
    """Setup logging and metrics for management operations."""
    logger.info(
        {
            f"manager api, {func_name} router usr_input": json.dumps(
                run_params_list, ensure_ascii=False
            )
        }
    )
    span_context.add_info_events(
        {"usr_input": json.dumps(run_params_list, ensure_ascii=False)}
    )
    return Meter(app_id=span_context.app_id, func=func_name)


def handle_validation_error_mgmt(
    validate_err, span_context, node_trace, m, error_code=None
):
    """Handle validation errors with telemetry."""
    if error_code is None:
        error_code = ErrCode.JSON_SCHEMA_VALIDATE_ERR

    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        m.in_error_count(error_code.code)
        node_trace.answer = validate_err
        node_trace.status = Status(
            code=error_code.code,
            message=validate_err,
        )
        send_telemetry_mgmt(node_trace)

    return ToolManagerResponse(
        code=error_code.code,
        message=validate_err,
        sid=span_context.sid,
        data={},
    )


def handle_success_response_mgmt(span_context, node_trace, m, data, tool_ids=None):
    """Handle successful response with telemetry."""
    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        m.in_success_count()
        node_trace.answer = (
            json.dumps(data, ensure_ascii=False)
            if isinstance(data, (dict, list))
            else str(data)
        )
        if tool_ids:
            node_trace.service_id = str(tool_ids)
        node_trace.status = Status(
            code=ErrCode.SUCCESSES.code,
            message=ErrCode.SUCCESSES.msg,
        )
        send_telemetry_mgmt(node_trace)

    return ToolManagerResponse(
        code=ErrCode.SUCCESSES.code,
        message=ErrCode.SUCCESSES.msg,
        sid=span_context.sid,
        data=data if isinstance(data, dict) else {"result": data},
    )


def handle_error_response_mgmt(err, span_context, node_trace, m, error_code=None):
    """Handle error responses with telemetry."""
    if error_code is None:
        error_code = ErrCode.COMMON_ERR

    message = str(err) if isinstance(err, Exception) else err
    span_context.add_error_event(message)
    span_context.set_status(OTelStatus(StatusCode.ERROR))

    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        m.in_error_count(error_code.code)
        node_trace.answer = message
        node_trace.status = Status(
            code=error_code.code,
            message=message,
        )
        send_telemetry_mgmt(node_trace)

    return ToolManagerResponse(
        code=error_code.code,
        message=message,
        sid=span_context.sid,
        data={},
    )


def validate_openapi_schema(tool, span_context):
    """Validate OpenAPI schema for a tool."""
    open_api_schema = tool.get("openapi_schema", "")
    schema_type = tool.get("schema_type", 0)
    validate_schema = OpenapiSchemaValidator(
        schema=open_api_schema, schema_type=schema_type, span=span_context
    )
    err = validate_schema.schema_validate()
    if err:
        return None, err
    return validate_schema.get_schema_dumps(), None


def process_tools_for_creation(tools, app_id, span_context):
    """Process tools for creation, including validation and ID generation."""
    tool_info = []
    for tool in tools:
        new_id = f"{hex(gen_id())}"
        tool_id = f"tool@{new_id[2:]}"

        schema_content, err = validate_openapi_schema(tool, span_context)
        if err:
            return (
                None,
                f"create tool: failed to validate tool {tool.get('name', '')} "
                f"openapi schema, reason {err}",
            )

        tool_name = tool.get("name", "")
        tool_description = tool.get("description", "")
        version = tool.get("version", const.DEF_VER)

        tool_info.append(
            {
                "app_id": app_id,
                "tool_id": tool_id,
                "schema": schema_content,
                "name": tool_name,
                "description": tool_description,
                "version": version,
                "is_deleted": const.DEF_DEL,
            }
        )

    return tool_info, None


def validate_tool_ids(tool_ids):
    """Validate tool ID format."""
    for tool_id in tool_ids:
        if not re.compile("^tool@[0-9a-zA-Z]+$").match(tool_id):
            return f"tool id {tool_id} illegal"
    return None


def process_tools_for_update(tools, app_id, span_context):
    """Process tools for update, including validation."""
    update_tool = []
    tool_ids = []

    for tool in tools:
        # Validate required fields
        required_fields = ["version", "name", "description", "openapi_schema"]
        for field in required_fields:
            if field not in tool:
                raise Exception(f"no {field} attr found in tool info!")

        schema_content = tool.get("openapi_schema", "")
        if schema_content:
            validated_schema, err = validate_openapi_schema(tool, span_context)
            if err:
                return (
                    None,
                    f"update tool: failed to validate tool {tool.get('id')} schema, "
                    f"reason {json.dumps(err)}",
                )
            schema_content = validated_schema

        update_tool.append(
            {
                "app_id": app_id,
                "tool_id": tool.get("id"),
                "name": tool.get("name"),
                "description": tool.get("description"),
                "open_api_schema": schema_content,
                "version": tool.get("version", const.DEF_VER),
                "is_deleted": const.DEF_DEL,
            }
        )
        tool_ids.append(tool.get("id"))

    return update_tool, tool_ids


def create_version(tools_info: ToolCreateRequest) -> ToolManagerResponse:
    """Create tool versions."""
    try:
        run_params_list = tools_info.model_dump(exclude_none=True)
        app_id, uid, caller, tool_type = extract_management_params(run_params_list)
        tools = run_params_list.get("payload", {}).get("tools")

        span, node_trace = setup_span_and_trace_mgmt(
            run_params_list, app_id, uid, caller, tool_type
        )

        with span.start(func_name="create_tools") as span_context:
            node_trace.sid = span_context.sid
            node_trace.chat_id = span_context.sid
            m = setup_logging_and_metrics_mgmt(
                span_context, run_params_list, "create_tools"
            )

            span_context.set_attributes(
                attributes={
                    "tools": str(run_params_list.get("payload", {}).get("tools"))
                }
            )

            # Validate API
            validate_err = api_validate(get_create_tool_schema(), run_params_list)
            if validate_err:
                return handle_validation_error_mgmt(
                    validate_err, span_context, node_trace, m
                )

            # Process tools
            tool_info, err = process_tools_for_creation(tools, app_id, span_context)
            if err:
                return handle_error_response_mgmt(
                    err,
                    span_context,
                    node_trace,
                    m,
                    ErrCode.OPENAPI_SCHEMA_VALIDATE_ERR,
                )

            # Save tools
            crud_inst = ToolCrudOperation(get_db_engine())
            crud_inst.add_tools(tool_info)

            # Prepare response
            resp_tool = []
            tool_ids = []
            for tool in tool_info:
                resp_tool.append(
                    {
                        "name": tool.get("name"),
                        "id": tool.get("tool_id"),
                        "version": tool.get("version"),
                    }
                )
                tool_ids.append(tool.get("tool_id"))

            return handle_success_response_mgmt(
                span_context, node_trace, m, {"tools": resp_tool}, tool_ids
            )

    except Exception as err:
        logger.error(f"failed to create tools, reason {err}")
        return handle_error_response_mgmt(err, span_context, node_trace, m)


def delete_version(
    app_id: str = Query(),
    tool_ids: list[str] = Query(),
    versions: list[str] = Query(default=None),
) -> ToolManagerResponse:
    """Delete tool versions."""
    uid = new_uid()
    caller = ""
    tool_type = ""
    span = Span(
        app_id=app_id if app_id else os.getenv(const.DEFAULT_APPID_KEY),
        uid=uid,
    )

    with span.start(func_name="delete_tools") as span_context:
        usr_input = {"app_id": app_id, "tool": tool_ids, "versions": versions}
        m = setup_logging_and_metrics_mgmt(span_context, usr_input, "delete_tools")

        span_context.set_attributes(attributes={"tool_ids": str(tool_ids)})
        span_context.set_attributes(attributes={"versions": str(versions)})

        node_trace = NodeTraceLog(
            service_id=str(tool_ids) + " " + str(versions),
            sid=span_context.sid,
            app_id=span_context.app_id,
            uid=span_context.uid,
            chat_id=span_context.sid,
            sub="spark-link",
            caller=caller,
            log_caller=tool_type,
            question=json.dumps(usr_input, ensure_ascii=False),
        )

        # Validate inputs
        if len(tool_ids) == 0:
            msg = f"del tool: tool num {len(tool_ids)} is 0"
            return handle_validation_error_mgmt(msg, span_context, node_trace, m)

        tool_id_error = validate_tool_ids(tool_ids)
        if tool_id_error:
            return handle_validation_error_mgmt(
                f"del tool: {tool_id_error}", span_context, node_trace, m
            )

        try:
            # Prepare tool info for deletion
            tool_info = []
            for index, tool_id in enumerate(tool_ids):
                try:
                    version = versions[index]
                except Exception:
                    version = ""
                tool_info.append(
                    {
                        "tool_id": tool_id,
                        "app_id": app_id,
                        "version": version,
                        "is_deleted": const.DEF_DEL,
                    }
                )

            # Delete tools
            crud_inst = ToolCrudOperation(get_db_engine())
            crud_inst.delete_tools(tool_info)

            return handle_success_response_mgmt(
                span_context, node_trace, m, ErrCode.SUCCESSES.msg
            )

        except Exception as err:
            logger.error(f"failed to del tool, reason {err}")
            return handle_error_response_mgmt(err, span_context, node_trace, m)


def update_version(tools_info: ToolUpdateRequest) -> ToolManagerResponse:
    """Update tool versions."""
    run_params_list = tools_info.model_dump(exclude_none=True)
    app_id, uid, caller, tool_type = extract_management_params(run_params_list)
    tool_type = ""  # Override to empty as in original

    span, node_trace = setup_span_and_trace_mgmt(
        run_params_list, app_id, uid, caller, tool_type
    )

    with span.start(func_name="update_tools") as span_context:
        node_trace.sid = span_context.sid
        node_trace.chat_id = span_context.sid
        m = setup_logging_and_metrics_mgmt(
            span_context, run_params_list, "update_tools"
        )

        span_context.set_attributes(
            attributes={"tools": str(run_params_list.get("payload", {}).get("tools"))}
        )

        # Validate API
        validate_err = api_validate(get_update_tool_schema(), run_params_list)
        if validate_err:
            return handle_validation_error_mgmt(
                validate_err,
                span_context,
                node_trace,
                m,
                ErrCode.JSON_PROTOCOL_PARSER_ERR,
            )

        try:
            tools = run_params_list.get("payload", {}).get("tools")
            update_tool, tool_ids = process_tools_for_update(
                tools, app_id, span_context
            )

            # Save updated tools
            crud_inst = ToolCrudOperation(get_db_engine())
            crud_inst.add_tool_version(update_tool)

            return handle_success_response_mgmt(
                span_context, node_trace, m, ErrCode.SUCCESSES.msg, tool_ids
            )

        except Exception as err:
            logger.error(f"failed to update tool, reason {err}")
            return handle_error_response_mgmt(err, span_context, node_trace, m)


def read_version(
    tool_ids: list[str] = Query(), app_id: str = Query(), versions: list[str] = Query()
) -> ToolManagerResponse:
    """Read tool versions."""
    uid = new_uid()
    caller = ""
    tool_type = ""
    span = Span(
        app_id=app_id if app_id else os.getenv(const.DEFAULT_APPID_KEY),
        uid=uid,
    )

    with span.start(func_name="read_tools") as span_context:
        usr_input = {"app_id": app_id, "tool": tool_ids, "versions": versions}
        m = setup_logging_and_metrics_mgmt(span_context, usr_input, "read_tools")

        span_context.set_attributes(attributes={"tool_ids": str(tool_ids)})
        span_context.set_attributes(attributes={"versions": str(versions)})

        node_trace = NodeTraceLog(
            service_id=str(tool_ids),
            sid=span_context.sid,
            app_id=span_context.app_id,
            uid=span_context.uid,
            chat_id=span_context.sid,
            sub="spark-link",
            caller=caller,
            log_caller=tool_type,
            question=json.dumps(usr_input, ensure_ascii=False),
        )

        # Validate inputs
        if len(tool_ids) == 0 or len(versions) == 0 or len(tool_ids) != len(versions):
            msg = (
                f"get tool: tool num {len(tool_ids)}, "
                f"version num {len(versions)} not equal"
            )
            return handle_validation_error_mgmt(msg, span_context, node_trace, m)

        tool_id_error = validate_tool_ids(tool_ids)
        if tool_id_error:
            return handle_validation_error_mgmt(
                f"get tool: {tool_id_error} pattern illegal",
                span_context,
                node_trace,
                m,
            )

        try:
            # Prepare tool info for reading
            tool_info = []
            for index, tool_id in enumerate(tool_ids):
                tool_info.append(
                    {
                        "tool_id": tool_id,
                        "app_id": app_id,
                        "version": versions[index],
                        "is_deleted": const.DEF_DEL,
                    }
                )

            try:
                crud_inst = ToolCrudOperation(get_db_engine())
                results = crud_inst.get_tools(tool_info, span=span_context)
            except SparkLinkBaseException as err:
                return handle_error_response_mgmt(
                    err.message, span_context, node_trace, m, err
                )

            # Process results
            tools = []
            for result in results:
                result_dict = result.dict()
                tools.append(
                    {
                        "name": result_dict.get("name", ""),
                        "description": result_dict.get("description", ""),
                        "id": result_dict.get("tool_id", ""),
                        "schema": result_dict.get("open_api_schema", ""),
                        "version": result_dict.get("version", ""),
                    }
                )

            return handle_success_response_mgmt(
                span_context, node_trace, m, {"tools": tools}
            )

        except Exception as err:
            logger.error(f"failed to get tool, reason {err}")
            return handle_error_response_mgmt(err, span_context, node_trace, m)
