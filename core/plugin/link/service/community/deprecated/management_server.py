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
from plugin.link.api.schemas.community.deprecated.management_schema import (
    ToolManagerRequest,
    ToolManagerResponse,
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


def _extract_request_params(run_params_list):
    """Extract common parameters from request."""
    header = run_params_list.get("header", {})
    return {
        "app_id": header.get("app_id") or os.getenv(const.DEFAULT_APPID_KEY),
        "uid": header.get("uid") or new_uid(),
        "caller": header.get("caller", ""),
        "tool_type": header.get("tool_type", ""),
        "sid": header.get("sid"),
    }


def _setup_observability(params, run_params_list, func_name):
    """Setup span and tracing for a function."""
    span = Span(app_id=params["app_id"], uid=params["uid"])
    if params["sid"]:
        span.sid = params["sid"]

    span_context = span.start(func_name=func_name)
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
        caller=params["caller"],
        log_caller=params["tool_type"],
        question=json.dumps(run_params_list, ensure_ascii=False),
    )

    meter = Meter(app_id=span_context.app_id, func=func_name)
    return span_context, node_trace, meter


def _send_error_telemetry(meter, node_trace, error_code, error_msg):
    """Send error telemetry data."""
    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        meter.in_error_count(error_code)
        node_trace.answer = error_msg
        node_trace.status = Status(code=error_code, message=error_msg)
        kafka_service = get_kafka_producer_service()
        node_trace.start_time = int(round(time.time() * 1000))
        kafka_service.send(
            os.getenv(const.KAFKA_TOPIC_KEY),
            node_trace.to_json()
        )


def _send_success_telemetry(meter, node_trace, response_data, service_id=None):
    """Send success telemetry data."""
    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        meter.in_success_count()
        node_trace.answer = json.dumps(response_data, ensure_ascii=False)
        if service_id:
            node_trace.service_id = str(service_id)
        node_trace.status = Status(
            code=ErrCode.SUCCESSES.code,
            message=ErrCode.SUCCESSES.msg,
        )
        kafka_service = get_kafka_producer_service()
        node_trace.start_time = int(round(time.time() * 1000))
        kafka_service.send(
            os.getenv(const.KAFKA_TOPIC_KEY),
            node_trace.to_json()
        )


def _validate_and_create_tool(tool, span_context):
    """Validate and prepare a single tool for creation."""
    new_id = f"{hex(gen_id())}"
    tool_id = f"tool@{new_id[2:]}"
    open_api_schema = tool.get("openapi_schema", "")
    schema_type = tool.get("schema_type", 0)

    validate_schema = OpenapiSchemaValidator(
        schema=open_api_schema, schema_type=schema_type, span=span_context
    )
    err = validate_schema.schema_validate()
    if err:
        msg = (
            f"create tool: failed to validate tool "
            f"{tool.get('name', '')} openapi schema, reason {err}"
        )
        span_context.add_error_event(msg)
        span_context.set_status(OTelStatus(StatusCode.ERROR))
        return None, err

    return {
        "tool_id": tool_id,
        "schema": validate_schema.get_schema_dumps(),
        "name": tool.get("name", ""),
        "description": tool.get("description", ""),
    }, None


def create_tools(tools_info: ToolManagerRequest):
    """
    description: Create tools
    :return:
    """
    try:
        run_params_list = tools_info.model_dump()
        params = _extract_request_params(run_params_list)
        tools = run_params_list.get("payload", {}).get("tools")

        logger.info(
            {
                "manager api, create_tools router usr_input": json.dumps(
                    run_params_list, ensure_ascii=False
                )
            }
        )

        span_context, node_trace, meter = _setup_observability(
            params, run_params_list, "create_tools"
        )

        with span_context:
            span_context.set_attributes(
                attributes={
                    "tools": str(run_params_list.get("payload", {}).get("tools"))
                }
            )

            # Validate API
            validate_err = api_validate(get_create_tool_schema(), run_params_list)
            if validate_err:
                _send_error_telemetry(
                    meter,
                    node_trace,
                    ErrCode.JSON_SCHEMA_VALIDATE_ERR.code,
                    validate_err,
                )
                return ToolManagerResponse(
                    code=ErrCode.JSON_SCHEMA_VALIDATE_ERR.code,
                    message=validate_err,
                    sid=span_context.sid,
                    data={},
                )

            # Process tools
            tool_info = []
            tool_ids = []
            for tool in tools:
                tool_data, err = _validate_and_create_tool(tool, span_context)
                if err:
                    _send_error_telemetry(
                        meter,
                        node_trace,
                        ErrCode.OPENAPI_SCHEMA_VALIDATE_ERR.code,
                        json.dumps(err),
                    )
                    return ToolManagerResponse(
                        code=ErrCode.OPENAPI_SCHEMA_VALIDATE_ERR.code,
                        message=json.dumps(err),
                        sid=span_context.sid,
                        data={},
                    )

                tool_info.append(
                    {
                        "app_id": params["app_id"],
                        "tool_id": tool_data["tool_id"],
                        "schema": tool_data["schema"],
                        "name": tool_data["name"],
                        "description": tool_data["description"],
                        "version": const.DEF_VER,
                        "is_deleted": const.DEF_DEL,
                    }
                )

            # Save to database
            crud_inst = ToolCrudOperation(get_db_engine())
            crud_inst.add_tools(tool_info)

            # Prepare response
            resp_tool = []
            for tool in tool_info:
                resp_tool.append({"name": tool.get("name"), "id": tool.get("tool_id")})
                tool_ids.append(tool.get("tool_id"))

            _send_success_telemetry(meter, node_trace, resp_tool, tool_ids)
            return ToolManagerResponse(
                code=ErrCode.SUCCESSES.code,
                message=ErrCode.SUCCESSES.msg,
                sid=span_context.sid,
                data={"tools": resp_tool},
            )

    except Exception as err:
        logger.error(f"failed to create tools, reason {err}")
        _send_error_telemetry(meter, node_trace, ErrCode.COMMON_ERR.code, str(err))
        return ToolManagerResponse(
            code=ErrCode.COMMON_ERR.code,
            message=str(err),
            sid=span_context.sid,
            data={},
        )


def _validate_read_tool_ids(tool_ids, span_context, meter, node_trace):
    """Validate tool IDs for reading."""
    if len(tool_ids) == 0:
        msg = f"get tool: tool num {len(tool_ids)} not in threshold 0 ~ 6"
        span_context.add_error_event(msg)
        span_context.set_status(OTelStatus(StatusCode.ERROR))
        _send_error_telemetry(
            meter, node_trace, ErrCode.JSON_SCHEMA_VALIDATE_ERR.code, msg
        )
        return ToolManagerResponse(
            code=ErrCode.JSON_SCHEMA_VALIDATE_ERR.code,
            message=msg,
            sid=span_context.sid,
            data={},
        )

    for tool_id in tool_ids:
        if not re.compile("^tool@[0-9a-zA-Z]+$").match(tool_id):
            msg = f"get tool: tool id {tool_id} pattern illegal"
            span_context.add_error_event(msg)
            span_context.set_status(OTelStatus(StatusCode.ERROR))
            _send_error_telemetry(
                meter, node_trace, ErrCode.JSON_SCHEMA_VALIDATE_ERR.code, msg
            )
            return ToolManagerResponse(
                code=ErrCode.JSON_SCHEMA_VALIDATE_ERR.code,
                message=msg,
                sid=span_context.sid,
                data={},
            )
    return None


def _process_database_results(results):
    """Process database results into response format."""
    tools = []
    for result in results:
        result_dict = result.dict()
        tools.append(
            {
                "name": result_dict.get("name", ""),
                "description": result_dict.get("description", ""),
                "id": result_dict.get("tool_id", ""),
                "schema": result_dict.get("open_api_schema", ""),
            }
        )
    return tools


def _validate_and_process_update_tool(tool, app_id, span_context):
    """Validate and process a single tool for update."""
    schema_content = tool.get("openapi_schema", "")
    if not schema_content:
        return None, None

    schema_type = tool.get("schema_type", 0)
    validate_schema = OpenapiSchemaValidator(
        schema=schema_content,
        schema_type=schema_type,
        span=span_context,
    )
    err = validate_schema.schema_validate()
    if err:
        msg = (
            f"update tool: failed to validate tool {tool.get('id')} schema,"
            f" reason {json.dumps(err)}"
        )
        span_context.add_error_event(msg)
        span_context.set_status(OTelStatus(StatusCode.ERROR))
        return None, err

    schema_content = validate_schema.get_schema_dumps()
    update_tool_data = {
        "app_id": app_id,
        "tool_id": tool.get("id"),
        "name": tool.get("name", None),
        "description": tool.get("description", None),
        "open_api_schema": schema_content,
        "version": const.DEF_VER,
        "is_deleted": const.DEF_DEL,
    }
    return update_tool_data, None


def _validate_tool_ids(tool_ids, span_context, meter, node_trace):
    """Validate tool IDs for deletion."""
    if len(tool_ids) == 0 or len(tool_ids) > 6:
        msg = f"del tool: tool num {len(tool_ids)} not in threshold 1 ~ 6"
        span_context.add_error_event(msg)
        span_context.set_status(OTelStatus(StatusCode.ERROR))
        _send_error_telemetry(
            meter, node_trace, ErrCode.JSON_SCHEMA_VALIDATE_ERR.code, msg
        )
        return ToolManagerResponse(
            code=ErrCode.JSON_SCHEMA_VALIDATE_ERR.code,
            message=msg,
            sid=span_context.sid,
            data={},
        )

    for tool_id in tool_ids:
        if not re.compile("^tool@[0-9a-zA-Z]+$").match(tool_id):
            msg = f"del tool: tool id {tool_id} illegal"
            span_context.add_error_event(msg)
            span_context.set_status(OTelStatus(StatusCode.ERROR))
            _send_error_telemetry(
                meter, node_trace, ErrCode.JSON_SCHEMA_VALIDATE_ERR.code, msg
            )
            return ToolManagerResponse(
                code=ErrCode.JSON_SCHEMA_VALIDATE_ERR.code,
                message=msg,
                sid=span_context.sid,
                data={},
            )
    return None


def delete_tools(tool_ids: list[str] = Query(), app_id: str = Query()):
    """
    description: Delete tools
    :return:
    """
    uid = new_uid()
    caller = ""
    tool_type = ""
    span = Span(
        app_id=app_id if app_id else os.getenv(const.DEFAULT_APPID_KEY),
        uid=uid,
    )
    with span.start(func_name="delete_tools") as span_context:
        usr_input = {"app_id": app_id, "tool": tool_ids}
        logger.info(
            {
                "manager api, delete_tools router usr_input": json.dumps(
                    usr_input, ensure_ascii=False
                )
            }
        )
        span_context.add_info_events(
            {"usr_input": json.dumps(usr_input, ensure_ascii=False)}
        )
        span_context.set_attributes(attributes={"tool_ids": str(tool_ids)})
        node_trace = NodeTraceLog(
            service_id=str(tool_ids),
            sid=span_context.sid,
            app_id=span_context.app_id,
            uid=span_context.uid,
            chat_id=span_context.sid,
            sub="spark-link",
            caller=caller,
            log_caller=tool_type,
            question=json.dumps(tool_ids, ensure_ascii=False),
        )

        meter = Meter(app_id=span_context.app_id, func="delete_tools")

        # Validate tool IDs
        validation_error = _validate_tool_ids(tool_ids, span_context, meter, node_trace)
        if validation_error:
            return validation_error

        try:
            # Prepare tool info for deletion
            tool_info = [
                {
                    "tool_id": tool_id,
                    "app_id": app_id,
                    "version": const.DEF_VER,
                    "is_deleted": const.DEF_DEL,
                }
                for tool_id in tool_ids
            ]

            # Delete tools
            crud_inst = ToolCrudOperation(get_db_engine())
            crud_inst.delete_tools(tool_info)

            _send_success_telemetry(meter, node_trace, ErrCode.SUCCESSES.msg)
            return ToolManagerResponse(
                code=ErrCode.SUCCESSES.code,
                message=ErrCode.SUCCESSES.msg,
                sid=span_context.sid,
                data={},
            )
        except Exception as err:
            msg = f"failed to del tool, reason {err}"
            logger.error(msg)
            span_context.add_error_event(msg)
            span_context.set_status(OTelStatus(StatusCode.ERROR))
            _send_error_telemetry(meter, node_trace, ErrCode.COMMON_ERR.code, str(err))
            return ToolManagerResponse(
                code=ErrCode.COMMON_ERR.code,
                message=str(err),
                sid=span_context.sid,
                data={},
            )


def update_tools(tools_info: ToolManagerRequest):
    """
    description: Update tools
    :return:
    """
    try:
        run_params_list = tools_info.model_dump()
        params = _extract_request_params(run_params_list)
        tools = run_params_list.get("payload", {}).get("tools")

        logger.info(
            {
                "manager api, update_tools router usr_input": json.dumps(
                    run_params_list, ensure_ascii=False
                )
            }
        )

        span_context, node_trace, meter = _setup_observability(
            params, run_params_list, "update_tools"
        )

        with span_context:
            span_context.set_attributes(
                attributes={
                    "tools": str(run_params_list.get("payload", {}).get("tools"))
                }
            )

            # Validate API
            validate_err = api_validate(get_update_tool_schema(), run_params_list)
            if validate_err:
                _send_error_telemetry(
                    meter,
                    node_trace,
                    ErrCode.JSON_PROTOCOL_PARSER_ERR.code,
                    validate_err,
                )
                return ToolManagerResponse(
                    code=ErrCode.JSON_PROTOCOL_PARSER_ERR.code,
                    message=validate_err,
                    sid=span_context.sid,
                    data={},
                )

            # Process tools
            update_tool = []
            tool_ids = []
            for tool in tools:
                tool_data, err = _validate_and_process_update_tool(
                    tool, params["app_id"], span_context
                )
                if tool_data is None and err is None:
                    continue  # Skip tools without schema content
                if err:
                    _send_error_telemetry(
                        meter,
                        node_trace,
                        ErrCode.OPENAPI_SCHEMA_VALIDATE_ERR.code,
                        json.dumps(err),
                    )
                    return ToolManagerResponse(
                        code=ErrCode.OPENAPI_SCHEMA_VALIDATE_ERR.code,
                        message=json.dumps(err),
                        sid=span_context.sid,
                        data={},
                    )

                update_tool.append(tool_data)
                tool_ids.append(tool.get("id"))

            # Update tools in database
            crud_inst = ToolCrudOperation(get_db_engine())
            crud_inst.update_tools(update_tool)

            _send_success_telemetry(meter, node_trace, ErrCode.SUCCESSES.msg, tool_ids)
            return ToolManagerResponse(
                code=ErrCode.SUCCESSES.code,
                message=ErrCode.SUCCESSES.msg,
                sid=span_context.sid,
                data={},
            )

    except Exception as err:
        msg = f"failed to update tool, reason {err}"
        logger.error(msg)
        _send_error_telemetry(meter, node_trace, ErrCode.COMMON_ERR.code, str(err))
        return ToolManagerResponse(
            code=ErrCode.COMMON_ERR.code,
            message=str(err),
            sid=span_context.sid,
            data={},
        )


def read_tools(tool_ids: list[str] = Query(), app_id: str = Query()):
    """
    description: Get tools
    :return:
    """
    uid = new_uid()
    caller = ""
    tool_type = ""
    span = Span(
        app_id=app_id if app_id else os.getenv(const.DEFAULT_APPID_KEY),
        uid=uid,
    )
    with span.start(func_name="read_tools") as span_context:
        usr_input = {"app_id": app_id, "tool": tool_ids}
        logger.info(
            {
                "manager api, read_tools router usr_input": json.dumps(
                    usr_input, ensure_ascii=False
                )
            }
        )
        span_context.add_info_events(
            {"usr_input": json.dumps(usr_input, ensure_ascii=False)}
        )
        span_context.set_attributes(attributes={"tool_ids": str(tool_ids)})
        node_trace = NodeTraceLog(
            service_id=str(tool_ids),
            sid=span_context.sid,
            app_id=span_context.app_id,
            uid=span_context.uid,
            chat_id=span_context.sid,
            sub="spark-link",
            caller=caller,
            log_caller=tool_type,
            question=json.dumps(tool_ids, ensure_ascii=False),
        )

        meter = Meter(app_id=span_context.app_id, func="read_tools")

        # Validate tool IDs
        validation_error = _validate_read_tool_ids(
            tool_ids, span_context, meter, node_trace
        )
        if validation_error:
            return validation_error

        try:
            # Prepare tool info for query
            tool_info = [
                {
                    "tool_id": tool_id,
                    "app_id": app_id,
                    "version": const.DEF_VER,
                    "is_deleted": const.DEF_DEL,
                }
                for tool_id in tool_ids
            ]

            # Get tools from database
            try:
                crud_inst = ToolCrudOperation(get_db_engine())
                results = crud_inst.get_tools(tool_info, span=span_context)
            except SparkLinkBaseException as err:
                span_context.add_error_event(err.message)
                span_context.set_status(OTelStatus(StatusCode.ERROR))
                _send_error_telemetry(meter, node_trace, err.code, err.message)
                return ToolManagerResponse(
                    code=err.code, message=err.message, sid=span_context.sid, data={}
                )

            # Process results
            tools = _process_database_results(results)

            _send_success_telemetry(meter, node_trace, ErrCode.SUCCESSES.msg)
            return ToolManagerResponse(
                code=ErrCode.SUCCESSES.code,
                message=ErrCode.SUCCESSES.msg,
                sid=span_context.sid,
                data={"tools": tools},
            )

        except Exception as err:
            logger.error(f"failed to get tool, reason {err}")
            span_context.add_error_event(f"failed to get tool, reason {err}")
            span_context.set_status(OTelStatus(StatusCode.ERROR))
            _send_error_telemetry(meter, node_trace, ErrCode.COMMON_ERR.code, str(err))
            return ToolManagerResponse(
                code=ErrCode.COMMON_ERR.code,
                message=str(err),
                sid=span_context.sid,
                data={},
            )
