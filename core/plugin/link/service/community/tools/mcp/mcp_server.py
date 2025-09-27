"""MCP (Model Context Protocol) server integration module.

This module provides FastAPI endpoints and utilities for interacting with MCP servers.
It handles tool listing, tool execution, and server management operations with proper
error handling, observability tracing, and security validations.
"""

import os
import time
from typing import Tuple

from common.otlp.log_trace.node_trace_log import NodeTraceLog, Status
from common.otlp.metrics.meter import Meter
from common.otlp.trace.span import Span
from common.service import get_kafka_producer_service
from fastapi import Body
from loguru import logger
from mcp import ClientSession
from mcp.client.sse import sse_client
from opentelemetry.trace import Status as OTelStatus
from opentelemetry.trace import StatusCode
from plugin.link.api.schemas.community.tools.mcp.mcp_tools_schema import (
    MCPCallToolData,
    MCPCallToolRequest,
    MCPCallToolResponse,
    MCPImageResponse,
    MCPInfo,
    MCPItemInfo,
    MCPTextResponse,
    MCPToolListData,
    MCPToolListRequest,
    MCPToolListResponse,
)
from plugin.link.consts import const
from plugin.link.domain.models.manager import get_db_engine
from plugin.link.infra.tool_crud.process import ToolCrudOperation
from plugin.link.utils.errors.code import ErrCode
from plugin.link.utils.security.access_interceptor import is_in_blacklist, is_local_url
from plugin.link.utils.sid.sid_generator2 import new_sid


async def _process_mcp_server_by_id(mcp_server_id: str, span_context) -> MCPItemInfo:
    """Process a single MCP server by ID and return its tools."""
    err, url = get_mcp_server_url(mcp_server_id=mcp_server_id, span=span_context)
    if err is not ErrCode.SUCCESSES:
        return MCPItemInfo(
            server_id=mcp_server_id,
            server_status=err.code,
            server_message=err.msg,
            tools=[],
        )

    if is_local_url(url):
        err = ErrCode.MCP_SERVER_LOCAL_URL_ERR
        return MCPItemInfo(
            server_id=mcp_server_id,
            server_status=err.code,
            server_message=err.msg,
            tools=[],
        )

    return await _connect_and_get_tools(url, server_id=mcp_server_id)


async def _process_mcp_server_by_url(url: str) -> MCPItemInfo:
    """Process a single MCP server by URL and return its tools."""
    if is_local_url(url):
        err = ErrCode.MCP_SERVER_LOCAL_URL_ERR
        return MCPItemInfo(
            server_url=str(url),
            server_status=err.code,
            server_message=err.msg,
            tools=[],
        )

    if is_in_blacklist(url=url):
        err = ErrCode.MCP_SERVER_BLACKLIST_URL_ERR
        return MCPItemInfo(
            server_url=str(url),
            server_status=err.code,
            server_message=err.msg,
            tools=[],
        )

    return await _connect_and_get_tools(url, server_url=url)


async def _connect_and_get_tools(
    url: str, server_id: str = None, server_url: str = None
) -> MCPItemInfo:
    """Connect to MCP server and retrieve tools."""
    try:
        async with sse_client(url=url) as (read, write):
            try:
                async with ClientSession(read, write, logging_callback=None) as session:
                    try:
                        await session.initialize()
                    except Exception:
                        err = ErrCode.MCP_SERVER_INITIAL_ERR
                        return MCPItemInfo(
                            server_id=server_id,
                            server_url=server_url,
                            server_status=err.code,
                            server_message=err.msg,
                            tools=[],
                        )

                    try:
                        tools_result = await session.list_tools()
                        tools_dict = tools_result.model_dump()["tools"]
                        tools = []
                        for tool in tools_dict:
                            tool_info = MCPInfo(
                                name=tool.get("name", "No name available"),
                                description=tool.get(
                                    "description", "No description available"
                                ),
                                inputSchema=tool.get("inputSchema"),
                            )
                            tools.append(tool_info)

                        success = ErrCode.SUCCESSES
                        return MCPItemInfo(
                            server_id=server_id,
                            server_url=server_url,
                            server_status=success.code,
                            server_message=success.msg,
                            tools=tools,
                        )
                    except Exception:
                        err = ErrCode.MCP_SERVER_TOOL_LIST_ERR
                        return MCPItemInfo(
                            server_id=server_id,
                            server_url=server_url,
                            server_status=err.code,
                            server_message=err.msg,
                            tools=[],
                        )
            except Exception:
                err = ErrCode.MCP_SERVER_SESSION_ERR
                return MCPItemInfo(
                    server_id=server_id,
                    server_url=server_url,
                    server_status=err.code,
                    server_message=err.msg,
                    tools=[],
                )
    except Exception:
        err = ErrCode.MCP_SERVER_CONNECT_ERR
        return MCPItemInfo(
            server_id=server_id,
            server_url=server_url,
            server_status=err.code,
            server_message=err.msg,
            tools=[],
        )


async def tool_list(list_info: MCPToolListRequest = Body()) -> MCPToolListResponse:
    """
    Get the list of tools.
    """
    session_id = new_sid()
    mcp_server_ids = list_info.mcp_server_ids
    mcp_server_urls = list_info.mcp_server_urls

    span = Span(
        app_id="appid_mcp",
        uid="mcp_uid",
    )

    if session_id:
        span.sid = session_id

    with span.start(func_name="tool_list") as span_context:
        logger.info(
            {"mcp api, tool_list router usr_input": list_info.model_dump_json()}
        )
        span_context.add_info_events({"usr_input": list_info.model_dump_json()})
        span_context.set_attributes(attributes={"tool_id": "tool_list"})
        node_trace = NodeTraceLog(
            service_id="",
            sid=span_context.sid,
            app_id=span_context.app_id,
            uid=span_context.uid,
            chat_id=span_context.sid,
            sub="spark-link",
            caller="mcp_caller",
            log_caller="",
            question=list_info.model_dump_json(),
        )
        m = Meter(app_id=span_context.app_id, func="tool_list")

        items = []

        # Process IDs
        if mcp_server_ids:
            for mcp_server_id in mcp_server_ids:
                item = await _process_mcp_server_by_id(mcp_server_id, span_context)
                items.append(item)

        # Process URLs
        if mcp_server_urls:
            for url in mcp_server_urls:
                item = await _process_mcp_server_by_url(url)
                items.append(item)

        success = ErrCode.SUCCESSES
        result = MCPToolListResponse(
            code=success.code,
            message=success.msg,
            sid=session_id,
            data=MCPToolListData(servers=items),
        )
        if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
            m.in_success_count()
            node_trace.answer = result.model_dump_json()
            node_trace.service_id = "tool_list"
            node_trace.log_caller = "mcp_type"
            node_trace.status = Status(
                code=success.code,
                message=success.msg,
            )
            kafka_service = get_kafka_producer_service()
            node_trace.start_time = int(round(time.time() * 1000))
            kafka_service.send(os.getenv(const.KAFKA_TOPIC_KEY), node_trace.to_json())
        return result


def _create_error_response(err: ErrCode, session_id: str) -> MCPCallToolResponse:
    """Create a standardized error response for MCP call tool failures."""
    return MCPCallToolResponse(
        code=err.code,
        message=err.msg,
        sid=session_id,
        data=MCPCallToolData(isError=None, content=None),
    )


def _log_error_to_kafka(
    err: ErrCode, node_trace: NodeTraceLog, mcp_server_id: str, m: Meter
):
    """Log error information to Kafka if OTLP is enabled."""
    if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
        m.in_error_count(err.code)
        node_trace.answer = err.msg
        node_trace.service_id = mcp_server_id
        node_trace.status = Status(
            code=err.code,
            message=err.msg,
        )
        kafka_service = get_kafka_producer_service()
        node_trace.start_time = int(round(time.time() * 1000))
        kafka_service.send(
            os.getenv(const.KAFKA_TOPIC_KEY),
            node_trace.to_json()
        )


async def _initialize_session(
    session,
    session_id: str,
    span_context,
    node_trace: NodeTraceLog,
    mcp_server_id: str,
    m: Meter,
):
    """Initialize MCP session with error handling."""
    try:
        await session.initialize()
    except Exception:
        err = ErrCode.MCP_SERVER_INITIAL_ERR
        span_context.add_error_event(err.msg)
        span_context.set_status(OTelStatus(StatusCode.ERROR))
        _log_error_to_kafka(err, node_trace, mcp_server_id, m)
        return _create_error_response(err, session_id)
    return None


async def _execute_tool_call(
    session,
    tool_name: str,
    tool_args: dict,
    session_id: str,
    span_context,
    node_trace: NodeTraceLog,
    mcp_server_id: str,
    m: Meter,
):
    """Execute the actual tool call and process response."""
    try:
        call_result = await session.call_tool(tool_name, arguments=tool_args)
        call_dict = call_result.model_dump()
        is_error = call_dict["isError"]
        content = []

        for data in call_dict["content"]:
            if data["type"] == "text":
                text = MCPTextResponse(text=data["text"])
                content.append(text)
            elif data["type"] == "image":
                image = MCPImageResponse(data=data["data"], mineType=data["mineType"])
                content.append(image)

        return is_error, content
    except Exception:
        err = ErrCode.MCP_SERVER_CALL_TOOL_ERR
        span_context.add_error_event(err.msg)
        span_context.set_status(OTelStatus(StatusCode.ERROR))
        _log_error_to_kafka(err, node_trace, mcp_server_id, m)
        return _create_error_response(err, session_id), None


async def _call_mcp_tool(
    url: str,
    tool_name: str,
    tool_args: dict,
    session_id: str,
    span_context,
    node_trace: NodeTraceLog,
    mcp_server_id: str,
    m: Meter,
) -> MCPCallToolResponse:
    """Execute the actual MCP tool call with proper error handling."""
    try:
        async with sse_client(url=url) as (read, write):
            try:
                async with ClientSession(read, write, logging_callback=None) as session:
                    # Initialize session
                    init_result = await _initialize_session(
                        session, session_id, span_context, node_trace, mcp_server_id, m
                    )
                    if init_result:
                        return init_result

                    # Execute tool call
                    call_result = await _execute_tool_call(
                        session,
                        tool_name,
                        tool_args,
                        session_id,
                        span_context,
                        node_trace,
                        mcp_server_id,
                        m,
                    )

                    if isinstance(call_result[0], MCPCallToolResponse):
                        return call_result[0]

                    is_error, content = call_result
                    success = ErrCode.SUCCESSES
                    return MCPCallToolResponse(
                        code=success.code,
                        message=success.msg,
                        sid=session_id,
                        data=MCPCallToolData(isError=is_error, content=content),
                    )
            except Exception:
                err = ErrCode.MCP_SERVER_SESSION_ERR
                span_context.add_error_event(err.msg)
                span_context.set_status(OTelStatus(StatusCode.ERROR))
                _log_error_to_kafka(err, node_trace, mcp_server_id, m)
                return _create_error_response(err, session_id)
    except Exception:
        err = ErrCode.MCP_SERVER_CONNECT_ERR
        span_context.add_error_event(err.msg)
        span_context.set_status(OTelStatus(StatusCode.ERROR))
        _log_error_to_kafka(err, node_trace, mcp_server_id, m)
        return _create_error_response(err, session_id)


def _validate_and_get_url(
    call_info: MCPCallToolRequest, session_id: str, span_context, m: Meter
) -> tuple[ErrCode, str]:
    """Validate URL and get it from database if needed."""
    url = call_info.mcp_server_url

    # Check blacklist first
    if url and is_in_blacklist(url=url):
        err = ErrCode.MCP_SERVER_BLACKLIST_URL_ERR
        if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
            m.in_error_count(err.code)
        return err, ""

    # Get URL from database if not provided
    if not url:
        err, url = get_mcp_server_url(
            mcp_server_id=call_info.mcp_server_id, span=span_context
        )
        if err is not ErrCode.SUCCESSES:
            if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
                m.in_error_count(err.code)
            return err, ""

    # Check local URL
    if is_local_url(url):
        err = ErrCode.MCP_SERVER_LOCAL_URL_ERR
        if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
            m.in_error_count(err.code)
        return err, ""

    return ErrCode.SUCCESSES, url


async def call_tool(call_info: MCPCallToolRequest = Body()) -> MCPCallToolResponse:
    """
    Call a tool.
    """
    session_id = new_sid()
    mcp_server_id = call_info.mcp_server_id
    tool_name = call_info.tool_name
    tool_args = call_info.tool_args

    span = Span(
        app_id="appid_mcp",
        uid="mcp_uid",
    )

    if session_id:
        span.sid = session_id

    with span.start(func_name="call_tool") as span_context:
        logger.info(
            {"mcp api, call_tool router usr_input": call_info.model_dump_json()}
        )
        span_context.add_info_events({"usr_input": call_info.model_dump_json()})
        span_context.set_attributes(attributes={"tool_id": str(mcp_server_id)})
        node_trace = NodeTraceLog(
            service_id="",
            sid=span_context.sid,
            app_id=span_context.app_id,
            uid=span_context.uid,
            chat_id=span_context.sid,
            sub="spark-link",
            caller="mcp_caller",
            log_caller="",
            question=call_info.model_dump_json(),
        )
        m = Meter(app_id=span_context.app_id, func="call_tool")

        # Validate URL and get it from database if needed
        err, url = _validate_and_get_url(call_info, session_id, span_context, m)
        if err is not ErrCode.SUCCESSES:
            if not call_info.mcp_server_url:
                node_trace.answer = err.msg
            return _create_error_response(err, session_id)

        # Call the MCP tool
        result = await _call_mcp_tool(
            url,
            tool_name,
            tool_args,
            session_id,
            span_context,
            node_trace,
            mcp_server_id,
            m,
        )

        # Log success if the call succeeded
        if result.code == ErrCode.SUCCESSES.code:
            if os.getenv(const.OTLP_ENABLE_KEY, "false").lower() == "true":
                m.in_success_count()
                node_trace.answer = result.model_dump_json()
                node_trace.service_id = mcp_server_id
                node_trace.log_caller = "mcp_type"
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

        return result


def get_mcp_server_url(mcp_server_id: str, span: Span) -> Tuple[ErrCode, str]:
    """Retrieve MCP server URL from database by server ID.

    Args:
        mcp_server_id: Unique identifier for the MCP server
        span: OpenTelemetry span for tracing

    Returns:
        Tuple containing error code and server URL string
    """
    if not mcp_server_id:
        return (ErrCode.MCP_SERVER_ID_EMPTY_ERR, "")

    tool_id_info = [{"app_id": "1232223", "tool_id": mcp_server_id}]
    try:
        crud_inst = ToolCrudOperation(get_db_engine())
        query_results = crud_inst.get_tools(tool_id_info, span=span)
    except Exception:
        return (ErrCode.MCP_CRUD_OPERATION_FAILED_ERR, "")

    if not query_results:
        return (ErrCode.MCP_SERVER_NOT_FOUND_ERR, "")

    mcp_server = ""
    for query_result in query_results:
        result_dict = query_result.dict()
        if result_dict.get("tool_id", "") != mcp_server_id:
            continue

        # Database extension mcp_server_url stores MCP URL data
        mcp_server = result_dict.get("mcp_server_url", "")
        break

    if not mcp_server:
        return (ErrCode.MCP_SERVER_URL_EMPTY_ERR, mcp_server)

    return (ErrCode.SUCCESSES, mcp_server)
