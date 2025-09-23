"""
Flow service module for managing workflow operations.

This module provides comprehensive functionality for workflow management including
creation, updates, retrieval, debugging, and execution of workflow instances.
"""

import json
import time
from typing import Any, Dict, cast

from sqlmodel import Session  # type: ignore

from workflow.cache import flow as flow_cache
from workflow.cache.engine import ENGINE_CACHE_PREFIX
from workflow.domain.entities.flow import FlowUpdate
from workflow.domain.entities.node_debug_vo import NodeDebugRespVo
from workflow.domain.models.ai_app import App
from workflow.domain.models.flow import Flow
from workflow.engine.callbacks.openai_types_sse import GenerateUsage
from workflow.engine.dsl_engine import WorkflowEngineFactory
from workflow.engine.entities.node_entities import NodeType
from workflow.engine.entities.output_mode import EndNodeOutputModeEnum
from workflow.engine.entities.variable_pool import ParamKey, VariablePool
from workflow.engine.entities.workflow_dsl import WorkflowDSL
from workflow.engine.node import SparkFlowEngineNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.engine.nodes.flow.flow_node import FlowNode
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.middleware.cache.base import BaseCacheService
from workflow.extensions.middleware.database.utils import session_getter
from workflow.extensions.middleware.getters import get_cache_service, get_db_service
from workflow.extensions.otlp.log_trace.workflow_log import WorkflowLog
from workflow.extensions.otlp.trace.span import Span
from workflow.repository import flow_dao, license_dao
from workflow.service import audit_service, ops_service
from workflow.utils.snowfake import get_id


def save(flow: Flow, app_info: App, session: Session, span: Span) -> Flow:
    """
    Save a new workflow to the database.

    :param flow: The flow object containing workflow data and metadata
    :param app_info: Application information including source details
    :param session: Database session for transaction management
    :param span: Tracing span for logging operations
    :return: The saved flow object with generated ID
    """
    # Create new flow instance with generated IDs and app source
    db_flow = Flow(
        group_id=get_id(),
        name=flow.name,
        data=flow.data,
        description=flow.description,
        status=flow.status,
        app_id=flow.app_id,
        source=app_info.actual_source,
        version="-1",  # Initial version for new flows
    )
    db_flow.id = get_id()

    # Persist to database
    session.add(db_flow)
    session.commit()
    session.refresh(db_flow)
    return db_flow


def update(
    session: Session, db_flow: Flow, flow: FlowUpdate, flow_id: str, current_span: Span
) -> None:
    """
    Update an existing workflow with new data and clear related cache.

    :param session: Database session for transaction management
    :param db_flow: The existing flow object to be updated
    :param flow: The flow update object containing new values
    :param flow_id: The ID of the flow being updated
    :param current_span: Tracing span for logging operations
    :raises Exception: If update fails, transaction is rolled back and exception is re-raised
    """
    try:
        # Update flow properties if provided
        if flow.name:
            db_flow.name = flow.name
        if flow.description:
            db_flow.description = flow.description
        if flow.app_id:
            db_flow.app_id = flow.app_id
        if flow.data:
            db_flow.data = flow.data
        if flow.status:
            db_flow.status = flow.status
            # Set release data when status is published (status > 0)
            if flow.status > 0:
                db_flow.release_data = db_flow.data

        session.add(db_flow)
        session.commit()

        # Clear engine cache for the updated flow
        cache_service = get_cache_service()
        cache_service.delete(key=f"{ENGINE_CACHE_PREFIX}:{flow_id}:{flow.app_id}")
        current_span.add_info_event(
            f"Cleared engine instance from redis: {ENGINE_CACHE_PREFIX}:{flow_id}:{flow.app_id}"
        )
    except Exception as e:
        current_span.record_exception(e)
        session.rollback()
        raise  # Re-raise the exception to be handled by the main function


def get(flow_id: str, session: Session, span: Span) -> Flow:
    """
    Retrieve a workflow by its ID from the database.

    :param flow_id: The unique identifier of the workflow
    :param session: Database session for querying
    :param span: Tracing span for logging operations
    :return: The flow object if found
    :raises CustomException: If flow with the given ID is not found
    """
    # TODO: Implement caching mechanism for better performance
    db_flow = session.query(Flow).filter_by(id=int(flow_id)).first()
    if not db_flow:
        raise CustomException(CodeEnum.FlowIDNotFound)
    return db_flow


def get_latest_published_flow_by(
    flow_id: str, app_alias_id: str, session: Session, span: Span, version: str = ""
) -> Flow:
    """
    Retrieve the latest published workflow by flow ID and app alias ID.

    This function first checks the cache for the workflow, then falls back to database
    query if not found. It also validates license permissions before returning the flow.

    :param flow_id: The unique identifier of the workflow
    :param app_alias_id: The alias ID of the application
    :param session: Database session for querying
    :param span: Tracing span for logging operations
    :param version: Optional version number of the workflow (empty string for latest)
    :return: The published flow object
    :raises CustomException: If flow not found, not authorized, or not published
    """
    # Check cache first for better performance
    if not version:
        flow = flow_cache.get_flow_by_flow_id_latest(flow_id)
    else:
        flow = flow_cache.get_flow_by_flow_id_version(flow_id, version)
    if flow:
        return flow

    # Query database if not found in cache
    db_flow = session.query(Flow).filter_by(id=int(flow_id)).first()
    if not db_flow:
        raise CustomException(CodeEnum.FlowIDNotFound)

    # Validate license permissions
    lic = license_dao.get_by(db_flow.group_id, app_alias_id, session)
    if not lic:
        raise CustomException(CodeEnum.AppFlowNotAuthBondErr)

    if not lic.status:
        raise CustomException(CodeEnum.AppFlowNoLicenseErr)

    # Get the latest published version of the flow
    published_flow = flow_dao.get_latest_published_flow_by(
        db_flow.group_id, session, version
    )
    if not published_flow:
        raise CustomException(CodeEnum.FlowNotPublish)

    # Cache the result for future requests
    if not version:
        flow_cache.set_flow_by_flow_id_latest(flow_id, published_flow)
    else:
        flow_cache.set_flow_by_flow_id_version(flow_id, version, published_flow)

    return published_flow


def gen_mcp_input_schema(flow: Flow) -> dict:
    """
    Generate MCP (Model Context Protocol) input schema from workflow definition.

    This function extracts input parameters from the start node of a workflow
    and generates a JSON schema that can be used for MCP integration.

    :param flow: The flow object containing workflow definition
    :return: Dictionary containing MCP input schema with name, description, and inputSchema
    """
    # Extract basic flow information
    flow_name = flow.name
    description = flow.description

    # Initialize JSON schema structure
    input_schema = {"type": "object", "properties": {}, "required": []}
    nodes = flow.release_data.get("data", {}).get("nodes", [])

    # Process each node to find start node and extract input parameters
    for node in nodes:
        node_type = node["id"].split(":")[0]
        if node_type != NodeType.START.value:
            continue

        # Extract outputs from start node (these become input parameters)
        start_node_outputs = node.get("data", {}).get("outputs", [])
        for output in start_node_outputs:
            var_name = output.get("name", "")
            is_required = output.get("required", False)
            var_description = output.get("schema", {}).get("description", "")
            var_type = output.get("schema", {}).get("type", "")
            allowed_file_type = output.get("allowedFileType", [])

            properties: Any = input_schema["properties"]

            # Add property with file type if specified
            if len(allowed_file_type) > 0:
                properties[var_name] = {
                    "type": var_type,
                    "description": var_description,
                    "fileType": allowed_file_type[0],
                }
            else:
                properties[var_name] = {
                    "type": var_type,
                    "description": var_description,
                }

            # Add to required list if marked as required
            if is_required:
                required_list: list[str] = cast(list[str], input_schema["required"])
                required_list.append(var_name)

    return {
        "name": flow_name,
        "description": description,
        "inputSchema": input_schema,
    }


async def node_debug(workflow_dsl: WorkflowDSL, span: Span) -> NodeDebugRespVo:
    """
    Execute node debugging for a single workflow node.

    This function performs input audit, executes the node, and performs output audit
    to ensure the node works correctly in isolation.

    :param workflow_dsl: The workflow DSL containing node definition and configuration
    :param span: Tracing span for logging debug operations
    :return: Node debug response containing execution results and metrics
    :raises CustomExceptionCM: If node execution fails
    """
    # Record start time for performance measurement
    time_start = time.time() * 1000
    span.add_info_event(f"node debug dsl: {workflow_dsl.dict()}")

    # Perform input audit for security and compliance
    await audit_service.node_debug_input_audit(workflow_dsl.dict(), span)

    # Initialize variable pool and create debug node instance
    variable_pool = VariablePool(protocol=workflow_dsl.nodes)
    node_instance = WorkflowEngineFactory.create_debug_node(workflow_dsl, span)

    # Disable retry mechanism for node debugging to get immediate feedback
    node_instance.retry_config.should_retry = False

    # Execute the node and get results
    res: NodeRunResult = await node_instance.async_execute(variable_pool, span=span)

    # Check execution status and raise exception if failed
    if res.status != WorkflowNodeExecutionStatus.SUCCEEDED and res.error:
        raise res.error

    # Calculate execution time
    time_cost = time.time() * 1000 - time_start

    # Build response object with execution results
    node_debug_resp_vo = NodeDebugRespVo(
        node_id=workflow_dsl.nodes[0].id,
        alias_name=res.alias_name,
        node_type=res.node_type,
        input=json.dumps(res.inputs, ensure_ascii=False),
        raw_output="" if res.raw_output is None else res.raw_output,
        output=json.dumps(res.outputs, ensure_ascii=False),
        node_exec_cost="%.3f" % (time_cost / 1000),
        token_cost=res.token_cost or GenerateUsage(),
    )

    # Perform output audit for security and compliance
    need_audit_content = (
        node_debug_resp_vo.raw_output + node_debug_resp_vo.output
        if node_debug_resp_vo.raw_output != node_debug_resp_vo.output
        else node_debug_resp_vo.raw_output
    )
    await audit_service.output_audit(need_audit_content, span)

    return node_debug_resp_vo


def build(
    flow_id: str, cache_service: BaseCacheService, session: Session, span: Span
) -> None:
    """
    Build and validate a workflow by creating its engine instance.

    This function clears any existing cache, retrieves the flow definition,
    and creates a workflow engine to validate the flow structure.

    :param flow_id: The unique identifier of the workflow to build
    :param cache_service: Cache service for managing workflow cache
    :param session: Database session for retrieving flow data
    :param span: Tracing span for logging build operations
    :raises CustomException: If flow not found or build fails
    :raises Exception: If unexpected error occurs during build process
    """
    # Initialize workflow trace for monitoring and reporting
    workflow_trace = WorkflowLog(
        flow_id=flow_id,
        sid=span.sid,
        app_id="",
        uid="",
        caller="workflow",
        bot_id="",
        chat_id="",
        log_caller="build",
    )

    try:
        # Clear existing cache for the flow
        if flow_id in cache_service:
            cache_service.delete(flow_id)

        # Retrieve flow definition from database
        flow = get(flow_id=flow_id, session=session, span=span)

        # Create workflow engine to validate flow structure
        WorkflowEngineFactory.create_engine(
            WorkflowDSL.parse_obj(flow.data.get("data")), span
        )

        # Report successful build
        ops_service.kafka_report(span=span, workflow_log=workflow_trace)
    except CustomException as err:
        # Handle known exceptions and report status
        workflow_trace.set_status(err.code, err.message)
        raise err
    except Exception as err:
        # Handle unexpected exceptions
        workflow_trace.set_status(
            CodeEnum.ProtocolBuildError.code, CodeEnum.ProtocolBuildError.msg
        )
        raise err
    finally:
        # Always report workflow trace status
        ops_service.kafka_report(span=span, workflow_log=workflow_trace)


def set_flow_node_output_mode(
    variable_pool: VariablePool,
    built_nodes: Dict[str, SparkFlowEngineNode],
    app_alias_id: str,
    span: Span,
) -> None:
    """
    Set output mode for flow nodes based on their end node configuration.

    This function iterates through built nodes, identifies flow nodes, and sets
    their output mode based on the configuration of their corresponding end nodes.

    :param variable_pool: Variable pool containing system parameters
    :param built_nodes: Dictionary of built workflow nodes
    :param app_alias_id: Application alias ID for license validation
    :param span: Tracing span for logging operations
    """
    for key in built_nodes:
        # Process only flow type nodes
        if key.startswith(NodeType.FLOW.value):
            node_instance = built_nodes[key].node_instance
            if not isinstance(node_instance, FlowNode):
                continue

            flow_id = node_instance.flowId if node_instance else ""
            if flow_id:
                # Query flow end node information
                with session_getter(get_db_service()) as session:
                    db_flow = get_latest_published_flow_by(
                        flow_id, app_alias_id, session, span
                    )

                # Find end node and extract output mode configuration
                for node in db_flow.data["data"]["nodes"]:
                    if (
                        node is not None
                        and node.get("id") is not None
                        and node["id"].startswith("node-end::")
                    ):
                        node_data = node.get("data")
                        if node_data is not None:
                            node_param = node_data.get("nodeParam")
                            if node_param is not None:
                                output_mode = node_param.get("outputMode")

                                # Special handling for prompt mode with single output
                                if (
                                    output_mode == EndNodeOutputModeEnum.PROMPT_MODE
                                    and key in built_nodes
                                    and len(
                                        node_instance.output_identifier
                                        if node_instance
                                        else []
                                    )
                                    == 1
                                ):
                                    output_mode = EndNodeOutputModeEnum.OLD_PROMPT_MODE

                                # Set output mode in variable pool
                                variable_pool.system_params.set(
                                    ParamKey.FlowOutputMode, output_mode, node_id=key
                                )
                                span.add_info_events(
                                    {"output_mode": f"{key}: {output_mode}"}
                                )
