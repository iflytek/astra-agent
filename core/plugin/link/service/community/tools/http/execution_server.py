"""HTTP execution server for community tools.

This module provides HTTP execution capabilities for community tools, including
HTTP request execution, tool debugging, and OpenAPI schema validation.
It handles authentication, parameter validation, and response processing.
"""

import base64
import json
import os
import time

from common.otlp.log_trace.node_trace_log import NodeTraceLog, Status
from common.otlp.metrics.meter import Meter
from common.otlp.trace.span import Span
from common.service import get_kafka_producer_service
from loguru import logger
from opentelemetry.trace import Status as OTelStatus
from opentelemetry.trace import StatusCode
from plugin.link.api.schemas.community.tools.http.execution_schema import (
    HttpRunRequest,
    HttpRunResponse,
    HttpRunResponseHeader,
    ToolDebugRequest,
    ToolDebugResponse,
    ToolDebugResponseHeader,
)
from plugin.link.consts import const
from plugin.link.domain.models.manager import get_db_engine, get_redis_engine
from plugin.link.exceptions.sparklink_exceptions import SparkLinkBaseException
from plugin.link.infra.tool_crud.process import ToolCrudOperation
from plugin.link.infra.tool_exector.process import HttpRun
from plugin.link.utils.errors.code import ErrCode
from plugin.link.utils.json_schemas.read_json_schemas import (
    get_http_run_schema,
    get_tool_debug_schema,
)
from plugin.link.utils.json_schemas.schema_validate import api_validate
from plugin.link.utils.open_api_schema.schema_parser import OpenapiSchemaParser
from plugin.link.utils.uid.generate_uid import new_uid

default_value = {
    " 'string'": "",
    " 'number'": 0,
    " 'object'": {},
    " 'array'": [],
    " 'boolean'": False,
    " 'integer'": 0,
}


def extract_request_params(run_params_list):
    """Extract common request parameters."""
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
    return app_id, uid, caller


def send_telemetry(node_trace):
    """Send telemetry data to Kafka."""
    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        kafka_service = get_kafka_producer_service()
        node_trace.start_time = int(round(time.time() * 1000))
        kafka_service.send(
            os.getenv(const.KAFKA_TOPIC_KEY),
            node_trace.to_json()
        )


def handle_validation_error(validate_err, span_context, node_trace, m):
    """Handle validation errors with telemetry."""
    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        m.in_error_count(ErrCode.JSON_PROTOCOL_PARSER_ERR.code)
        node_trace.answer = validate_err
        node_trace.status = Status(
            code=ErrCode.JSON_PROTOCOL_PARSER_ERR.code,
            message=validate_err,
        )
        send_telemetry(node_trace)

    return HttpRunResponse(
        header=HttpRunResponseHeader(
            code=ErrCode.JSON_PROTOCOL_PARSER_ERR.code,
            message=validate_err,
            sid=span_context.sid,
        ),
        payload={},
    )


def handle_sparklink_error(err, span_context, node_trace, m, tool_id="", tool_type=""):
    """Handle SparkLink base exceptions with telemetry."""
    span_context.add_error_event(err.message)
    span_context.set_status(OTelStatus(StatusCode.ERROR))

    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        m.in_error_count(err.code)
        node_trace.answer = err.message
        node_trace.service_id = tool_id
        if tool_type:
            node_trace.log_caller = tool_type
        node_trace.status = Status(
            code=err.code,
            message=err.message,
        )
        send_telemetry(node_trace)

    return HttpRunResponse(
        header=HttpRunResponseHeader(
            code=err.code, message=err.message, sid=span_context.sid
        ),
        payload={},
    )


def handle_custom_error(
    error_code, message, span_context, node_trace, m, tool_id="", tool_type=""
):
    """Handle custom errors with telemetry."""
    span_context.add_error_event(message)
    span_context.set_status(OTelStatus(StatusCode.ERROR))

    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        m.in_error_count(error_code.code)
        node_trace.answer = message
        node_trace.service_id = tool_id
        if tool_type:
            node_trace.log_caller = tool_type
        node_trace.status = Status(
            code=error_code.code,
            message=message,
        )
        send_telemetry(node_trace)

    return HttpRunResponse(
        header=HttpRunResponseHeader(
            code=error_code.code,
            message=message,
            sid=span_context.sid,
        ),
        payload={},
    )


def handle_general_exception(
    err, span_context, node_trace, m, tool_id="", tool_type=""
):
    """Handle general exceptions with telemetry."""
    span_context.add_error_event(f"{ErrCode.COMMON_ERR.msg}: {err}")
    span_context.set_status(OTelStatus(StatusCode.ERROR))

    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        m.in_error_count(ErrCode.COMMON_ERR.code)
        node_trace.answer = f"{ErrCode.COMMON_ERR.msg}: {err}"
        node_trace.service_id = tool_id
        if tool_type:
            node_trace.log_caller = tool_type
        node_trace.status = Status(
            code=ErrCode.COMMON_ERR.code,
            message=f"{ErrCode.COMMON_ERR.msg}: {err}",
        )
        send_telemetry(node_trace)

    return HttpRunResponse(
        header=HttpRunResponseHeader(
            code=ErrCode.COMMON_ERR.code,
            message=f"{ErrCode.COMMON_ERR.msg}: {err}",
            sid=span_context.sid,
        ),
        payload={},
    )


def process_authentication(operation_id_schema, message_header, message_query, tool_id):
    """Process authentication for the request."""
    if not operation_id_schema["security"]:
        return

    security_type = operation_id_schema["security_type"]
    if security_type not in operation_id_schema["security"]:
        raise Exception(f"Security type {security_type} not found in security schema")

    api_key_info = operation_id_schema["security"].get(security_type)
    redis_engine = get_redis_engine()
    redis_cache = redis_engine.get(f"spark_bot:tool_config:{tool_id}")
    if redis_cache is None:
        raise Exception("security: get redis_cache is none!")
    auth_info = redis_cache.get("authentication", {})
    if auth_info is None:
        raise Exception("security: redis_cache get authentication is none!")

    if api_key_info.get("type") == "apiKey":
        api_key_dict = auth_info.get("apiKey")
        if api_key_info.get("in") == "header":
            message_header.update(api_key_dict)
        elif api_key_info.get("in") == "query":
            message_query.update(api_key_dict)


def validate_response_schema(result_json, open_api_schema):
    """Validate response against schema and return error messages."""
    response_schema = get_response_schema(open_api_schema)
    import jsonschema

    errs = list(jsonschema.Draft7Validator(response_schema).iter_errors(result_json))
    er_msgs = []
    for err in errs:
        err_msg = err.message
        if err_msg.startswith("None is not of type"):
            key_type = err_msg.split("None is not of type")[1]
            key_type = key_type.strip("")
            path = err.json_path
            path_list = path.split(".")[1:]
            path_list_len = len(path_list)
            i = 0
            root = result_json
            while True:
                if i >= path_list_len - 1:
                    break
                path_ = path_list[i]
                if "[" in path_ and "]" in path_:
                    array_name, array_index = process_array(path_)
                    root = root.get(array_name)
                    root = root[array_index]
                else:
                    root = root.get(path_)
                i += 1
            path_end = path_list[-1]
            if "[" in path_end and "]" in path_end:
                array_name, array_index = process_array(path_end)
                if key_type in default_value:
                    root[array_name][array_index] = default_value.get(key_type)
                else:
                    er_msgs.append(
                        f"参数路径: {err.json_path}, 错误信息: {err.message}"
                    )
            else:
                if key_type in default_value:
                    root[path_end] = default_value.get(key_type)
                else:
                    er_msgs.append(
                        f"参数路径: {err.json_path}, 错误信息: {err.message}"
                    )
        else:
            er_msgs.append(f"参数路径: {err.json_path}, 错误信息: {err.message}")
    return er_msgs


def handle_success_response(result, span_context, node_trace, m, tool_id, tool_type):
    """Handle successful response with telemetry."""
    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        m.in_success_count()
        node_trace.answer = result
        node_trace.service_id = tool_id
        node_trace.log_caller = tool_type
        node_trace.status = Status(
            code=ErrCode.SUCCESSES.code,
            message=ErrCode.SUCCESSES.msg,
        )
        send_telemetry(node_trace)

    return HttpRunResponse(
        header=HttpRunResponseHeader(
            code=ErrCode.SUCCESSES.code,
            message=ErrCode.SUCCESSES.msg,
            sid=span_context.sid,
        ),
        payload={
            "text": {
                "text": result,
            }
        },
    )


def handle_debug_validation_error(
    validate_err, span_context, node_trace, m, tool_id, tool_type
):
    """Handle validation errors in tool debug with telemetry."""
    span_context.add_error_event(
        f"Error code: {ErrCode.JSON_PROTOCOL_PARSER_ERR.code}, "
        f"error message: {validate_err}"
    )

    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        m.in_error_count(ErrCode.JSON_PROTOCOL_PARSER_ERR.code)
        node_trace.answer = validate_err
        node_trace.service_id = tool_id
        node_trace.log_caller = tool_type
        node_trace.status = Status(
            code=ErrCode.JSON_PROTOCOL_PARSER_ERR.code,
            message=validate_err,
        )
        send_telemetry(node_trace)

    return HttpRunResponse(
        header=HttpRunResponseHeader(
            code=ErrCode.JSON_PROTOCOL_PARSER_ERR.code,
            message=validate_err,
            sid=span_context.sid,
        ),
        payload={},
    )


def handle_debug_success_response(
    result, span_context, node_trace, m, tool_id, tool_type
):
    """Handle successful debug response with telemetry."""
    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        m.in_success_count()
        node_trace.answer = result
        node_trace.service_id = tool_id
        node_trace.log_caller = tool_type
        node_trace.status = Status(
            code=ErrCode.SUCCESSES.code,
            message=ErrCode.SUCCESSES.msg,
        )
        send_telemetry(node_trace)

    return ToolDebugResponse(
        header=ToolDebugResponseHeader(
            code=ErrCode.SUCCESSES.code,
            message=ErrCode.SUCCESSES.msg,
            sid=span_context.sid,
        ),
        payload={
            "text": {
                "text": result,
            }
        },
    )


def process_message_params(message):
    """Process and decode message parameters."""
    message_header = (
        json.loads(base64.b64decode(message.get("header")).decode("utf-8"))
        if message.get("header")
        else {}
    )
    message_query = (
        json.loads(base64.b64decode(message.get("query")).decode("utf-8"))
        if message.get("query")
        else {}
    )
    path = (
        json.loads(base64.b64decode(message.get("path")).decode("utf-8"))
        if message.get("path")
        else {}
    )
    body = (
        json.loads(base64.b64decode(message.get("body")).decode("utf-8"))
        if message.get("body")
        else {}
    )
    return message_header, message_query, path, body


def setup_http_request(
    operation_id_schema, message_header, message_query, path, body, open_api_schema
):
    """Setup HTTP request instance."""
    return HttpRun(
        server=operation_id_schema["server_url"],
        method=operation_id_schema["method"],
        path=path,
        query=message_query,
        header=message_header,
        body=body,
        open_api_schema=open_api_schema,
    )


def process_http_result(
    result, open_api_schema, span_context, node_trace, m, tool_id, tool_type
):
    """Process HTTP call result and handle validation."""
    result_json = None
    try:
        result_json = json.loads(result)
    except Exception:
        result_json = result

    er_msgs = validate_response_schema(result_json, open_api_schema)
    if er_msgs:
        msg = ";".join(er_msgs)
        detailed_message = (
            f"错误信息：{ErrCode.RESPONSE_SCHEMA_VALIDATE_ERR.msg}, " f"详细信息：{msg}"
        )
        return handle_custom_error(
            ErrCode.RESPONSE_SCHEMA_VALIDATE_ERR,
            detailed_message,
            span_context,
            node_trace,
            m,
            tool_id,
            tool_type,
        )

    span_context.add_info_events({"before result": result})
    result = json.dumps(result_json, ensure_ascii=False)
    span_context.add_info_events({"after result": result})

    return handle_success_response(
        result, span_context, node_trace, m, tool_id, tool_type
    )


def setup_span_and_trace(run_params_list, app_id, uid, caller):
    """Setup span and node trace for the request."""
    span = Span(app_id=app_id, uid=uid)
    sid = run_params_list.get("header", {}).get("sid")
    if sid:
        span.sid = sid

    node_trace = NodeTraceLog(
        service_id="",
        sid=sid or "",
        app_id=app_id,
        uid=uid,
        chat_id=sid or "",
        sub="spark-link",
        caller=caller,
        log_caller="",
        question=json.dumps(run_params_list, ensure_ascii=False),
    )
    return span, node_trace


def setup_logging_and_metrics(span_context, run_params_list):
    """Setup logging and metrics for the request."""
    logger.info(
        {
            "exec api, http_run router usr_input": json.dumps(
                run_params_list, ensure_ascii=False
            )
        }
    )
    span_context.add_info_events(
        {"usr_input": json.dumps(run_params_list, ensure_ascii=False)}
    )
    span_context.set_attributes(
        attributes={
            "tool_id": str(run_params_list.get("parameter", {}).get("tool_id", {}))
        }
    )
    return Meter(app_id=span_context.app_id, func="http_run")


def get_tool_schema(run_params_list, tool_id, operation_id, version, span_context):
    """Get tool schema from database."""
    tool_id_info = [
        {
            "app_id": run_params_list["header"]["app_id"],
            "tool_id": tool_id,
            "version": version,
            "is_deleted": const.DEF_DEL,
        }
    ]
    crud_inst = ToolCrudOperation(get_db_engine())
    query_results = crud_inst.get_tools(tool_id_info, span=span_context)

    if not query_results:
        return None, None, None

    parser_result = {}
    for query_result in query_results:
        result_dict = query_result.dict()
        open_api_schema = json.loads(result_dict.get("open_api_schema"))
        tool_type = (
            os.getenv(const.OFFICIAL_TOOL_KEY)
            if open_api_schema.get("info").get("x-is-official")
            else os.getenv(const.THIRD_TOOL_KEY)
        )
        parser = OpenapiSchemaParser(open_api_schema, span=span_context)
        parser_result.update({result_dict["tool_id"]: parser.schema_parser()})

    tool_id_schema = parser_result[tool_id]
    operation_id_schema = tool_id_schema.get(operation_id, "")

    return operation_id_schema, tool_type, open_api_schema


def validate_and_get_params(run_params_list, span_context, node_trace, m):
    """Validate request and extract parameters."""
    validate_err = api_validate(get_http_run_schema(), run_params_list)
    if validate_err:
        return None, handle_validation_error(validate_err, span_context, node_trace, m)

    tool_id = run_params_list["parameter"]["tool_id"]
    operation_id = run_params_list["parameter"]["operation_id"]
    version = run_params_list["parameter"].get("version", None)

    if version is None or version == "":
        version = const.DEF_VER

    return {"tool_id": tool_id, "operation_id": operation_id, "version": version}, None


async def handle_request_execution(
    operation_id_schema,
    tool_type,
    open_api_schema,
    run_params_list,
    params,
    span_context,
    node_trace,
    m,
):
    """Handle the actual HTTP request execution."""
    try:
        message = run_params_list["payload"]["message"]
        message_header, message_query, path, body = process_message_params(message)

        try:
            process_authentication(
                operation_id_schema, message_header, message_query, params["tool_id"]
            )
        except Exception as auth_err:
            if "Security type" in str(auth_err):
                return handle_custom_error(
                    ErrCode.OPENAPI_AUTH_TYPE_ERR,
                    ErrCode.OPENAPI_AUTH_TYPE_ERR.msg,
                    span_context,
                    node_trace,
                    m,
                    params["tool_id"],
                    tool_type,
                )
            raise

        http_inst = setup_http_request(
            operation_id_schema,
            message_header,
            message_query,
            path,
            body,
            open_api_schema,
        )
        result = await http_inst.do_call(span_context)

        return process_http_result(
            result,
            open_api_schema,
            span_context,
            node_trace,
            m,
            params["tool_id"],
            tool_type,
        )

    except SparkLinkBaseException as err:
        return handle_sparklink_error(
            err, span_context, node_trace, m, params["tool_id"], tool_type
        )
    except Exception as err:
        return handle_general_exception(
            err, span_context, node_trace, m, params["tool_id"], tool_type
        )


async def execute_http_request(run_params_list, params, span_context, node_trace, m):
    """Execute the HTTP request with all validations."""
    try:
        operation_id_schema, tool_type, open_api_schema = get_tool_schema(
            run_params_list,
            params["tool_id"],
            params["operation_id"],
            params["version"],
            span_context,
        )
    except SparkLinkBaseException as err:
        return handle_sparklink_error(
            err, span_context, node_trace, m, params["tool_id"]
        )

    if not operation_id_schema:
        if operation_id_schema is None:
            message = f"{params['tool_id']} does not exist"
            return handle_custom_error(
                ErrCode.TOOL_NOT_EXIST_ERR,
                message,
                span_context,
                node_trace,
                m,
                params["tool_id"],
            )
        else:
            message = f"operation_id: {params['operation_id']} does not exist"
            return handle_custom_error(
                ErrCode.OPERATION_ID_NOT_EXIST_ERR,
                message,
                span_context,
                node_trace,
                m,
                params["tool_id"],
                tool_type,
            )

    return await handle_request_execution(
        operation_id_schema,
        tool_type,
        open_api_schema,
        run_params_list,
        params,
        span_context,
        node_trace,
        m,
    )


async def http_run(run_params: HttpRunRequest) -> HttpRunResponse:
    """HTTP run with version."""
    run_params_list = run_params.model_dump(exclude_none=True)
    app_id, uid, caller = extract_request_params(run_params_list)
    span, node_trace = setup_span_and_trace(run_params_list, app_id, uid, caller)

    with span.start(func_name="http_run") as span_context:
        node_trace.sid = span_context.sid
        node_trace.chat_id = span_context.sid
        m = setup_logging_and_metrics(span_context, run_params_list)

        params, error_response = validate_and_get_params(
            run_params_list, span_context, node_trace, m
        )
        if error_response:
            return error_response

        return await execute_http_request(
            run_params_list, params, span_context, node_trace, m
        )


async def tool_debug(tool_debug_params: ToolDebugRequest) -> ToolDebugResponse:
    """Tool debugging interface."""
    run_params_list = tool_debug_params.dict()
    app_id, uid, caller = extract_request_params(run_params_list)
    tool_id = (
        run_params_list.get("header", {}).get("tool_id")
        if run_params_list.get("header", {}).get("tool_id")
        else ""
    )

    span = Span(app_id=app_id, uid=uid)
    sid = run_params_list.get("header", {}).get("sid")
    if sid:
        span.sid = sid

    with span.start(func_name="tool_debug") as span_context:
        m = Meter(app_id=span_context.app_id, func="tool_debug")
        try:
            openapi_schema = json.loads(tool_debug_params.openapi_schema)
            logger.info(
                {
                    "exec api, tool_debug router usr_input": json.dumps(
                        run_params_list, ensure_ascii=False
                    )
                }
            )
            span_context.add_info_events(
                {"usr_input": json.dumps(run_params_list, ensure_ascii=False)}
            )
            span_context.set_attributes(
                attributes={"server": str(run_params_list.get("server", {}))}
            )
            tool_type = (
                os.getenv(const.OFFICIAL_TOOL_KEY)
                if openapi_schema.get("info").get("x-is-official")
                else os.getenv(const.THIRD_TOOL_KEY)
            )
            node_trace = NodeTraceLog(
                service_id=tool_id,
                sid=span_context.sid,
                app_id=span_context.app_id,
                uid=span_context.uid,
                chat_id=span_context.sid,
                sub="spark-link",
                caller=caller,
                log_caller="",
                question=json.dumps(run_params_list, ensure_ascii=False),
            )

            validate_err = api_validate(get_tool_debug_schema(), run_params_list)
            if validate_err:
                return handle_debug_validation_error(
                    validate_err, span_context, node_trace, m, tool_id, tool_type
                )

            http_inst = HttpRun(
                server=tool_debug_params.server,
                method=tool_debug_params.method,
                path=tool_debug_params.path if tool_debug_params.path else {},
                query=tool_debug_params.query if tool_debug_params.query else {},
                header=tool_debug_params.header if tool_debug_params.header else {},
                body=tool_debug_params.body if tool_debug_params.body else {},
                open_api_schema=openapi_schema,
            )
            result = await http_inst.do_call(span_context)
            result_json = None
            try:
                result_json = json.loads(result)
            except Exception:
                result_json = result

            er_msgs = validate_response_schema(result_json, openapi_schema)
            if er_msgs:
                msg = ";".join(er_msgs)
                detailed_message = (
                    f"错误信息：{ErrCode.RESPONSE_SCHEMA_VALIDATE_ERR.msg}, "
                    f"详细信息：{msg}"
                )
                return handle_custom_error(
                    ErrCode.RESPONSE_SCHEMA_VALIDATE_ERR,
                    detailed_message,
                    span_context,
                    node_trace,
                    m,
                    tool_id,
                    tool_type,
                )

            span_context.add_info_events({"before result": result})
            result = json.dumps(result_json, ensure_ascii=False)
            span_context.add_info_events({"after result": result})

            return handle_debug_success_response(
                result, span_context, node_trace, m, tool_id, tool_type
            )

        except SparkLinkBaseException as err:
            return handle_sparklink_error(
                err, span_context, node_trace, m, tool_id, tool_type
            )
        except Exception as err:
            return handle_general_exception(
                err, span_context, node_trace, m, tool_id, tool_type
            )


def process_array(name):
    """Process array notation in parameter names."""
    bracket_left_index = name.find("[")
    bracket_right_index = name.find("]")
    array_name = name[0:bracket_left_index]
    array_index = int(name[bracket_left_index + 1 : bracket_right_index])
    return array_name, array_index


def get_response_schema(openapi_schema):
    """Get response schema from tool's OpenAPI schema."""
    if openapi_schema is None:
        return {}
    paths = openapi_schema.get("paths", {})
    response_schema = {}
    for _, method_dict in paths.items():
        for _, method in method_dict.items():
            response_schema = (
                method.get("responses", {})
                .get("200", {})
                .get("content", {})
                .get("application/json", {})
                .get("schema", {})
            )
    return response_schema
