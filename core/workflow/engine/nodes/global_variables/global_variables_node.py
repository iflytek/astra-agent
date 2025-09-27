"""
Global Variables Node Module

This module provides functionality for managing global variables in workflow execution.
It includes a VariablesManage class for cache operations and a GlobalVariablesNode class
that extends BaseNode to handle global variable operations (set/get) in workflow nodes.
"""

import json
from typing import Any, Literal

from pydantic import Field
from workflow.engine.entities.variable_pool import VariablePool
from workflow.engine.nodes.base_node import BaseNode
from workflow.engine.nodes.entities.node_run_result import (
    NodeRunResult,
    WorkflowNodeExecutionStatus,
)
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.middleware.getters import get_cache_service
from workflow.extensions.otlp.log_trace.node_log import NodeLog
from workflow.extensions.otlp.trace.span import Span

# Cache expiration time for parameters (7 days in seconds)
PARAMETER_EXPIRE_TIME_S = 60 * 60 * 24 * 7

# Variable pool identifier constant
VARIABLE_POOL = "VARIABLE_POOL"

# Redis key prefix for variable pool storage
VARIABLE_POOL_PREFIX = "sparkflowV2:variable_pool"


class VariablesManage:
    """
    Manages global variables using cache service for workflow execution.

    This class provides methods to add, retrieve, and clear global variables
    stored in cache with hierarchical key structure based on flow, user, app, and chat context.
    """

    flow_id: str
    uid: str
    app_id: str
    chat_id: str

    def __init__(self, flow_id: str, uid: str, app_id: str, chat_id: str):
        """
        Initialize VariablesManage instance.

        :param flow_id: The workflow flow identifier
        :param uid: User identifier
        :param app_id: Application identifier
        :param chat_id: Chat session identifier (can be None for non-chat contexts)
        """
        self.flow_id = flow_id
        self.uid = uid
        self.app_id = app_id
        self.chat_id = chat_id

    def add_variable(self, variable_name: str, value: str) -> None:
        """
        Add a global variable to the cache.

        :param variable_name: Name of the variable to store
        :param value: Value to store for the variable
        """
        if self.chat_id is None:
            name = f"{VARIABLE_POOL_PREFIX}:{self.flow_id}:{self.uid}:{self.app_id}"
        else:
            name = f"{VARIABLE_POOL_PREFIX}:{self.flow_id}:{self.uid}:{self.app_id}:{self.chat_id}"
        cache_service = get_cache_service()
        # Store variable with no expiration (previously used PARAMETER_EXPIRE_TIME_S)
        cache_service.hash_set_ex(name, variable_name, value, None)

    def get_variable(self, variable_name: str) -> Any:
        """
        Retrieve a global variable from the cache.

        :param variable_name: Name of the variable to retrieve
        :return: The value of the variable, or None if not found
        """
        if self.chat_id is None:
            name = f"{VARIABLE_POOL_PREFIX}:{self.flow_id}:{self.uid}:{self.app_id}"
        else:
            name = f"{VARIABLE_POOL_PREFIX}:{self.flow_id}:{self.uid}:{self.app_id}:{self.chat_id}"
        cache_service = get_cache_service()
        return cache_service.hash_get(name, variable_name)

    def get_all_variables(self) -> dict:
        """
        Retrieve all global variables from the cache.

        :return: Dictionary containing all variables and their values
        """
        if self.chat_id is None:
            name = f"{VARIABLE_POOL_PREFIX}:{self.flow_id}:{self.uid}:{self.app_id}"
        else:
            name = f"{VARIABLE_POOL_PREFIX}:{self.flow_id}:{self.uid}:{self.app_id}:{self.chat_id}"
        cache_service = get_cache_service()
        return cache_service.hash_get_all(name)

    def clear(self) -> None:
        """
        Clear all global variables from the cache.

        :return: Result of the cache deletion operation
        """
        if self.chat_id is None:
            name = f"{VARIABLE_POOL_PREFIX}:{self.flow_id}:{self.uid}:{self.app_id}"
        else:
            name = f"{VARIABLE_POOL_PREFIX}:{self.flow_id}:{self.uid}:{self.app_id}:{self.chat_id}"
        cache_service = get_cache_service()
        return cache_service.delete(name)


class GlobalVariablesNode(BaseNode):
    """
    Workflow node for managing global variables.

    This node extends BaseNode to provide functionality for setting and getting
    global variables that can be shared across workflow execution contexts.
    Supports both 'set' and 'get' operations for variable management.
    """

    method: Literal["set", "get"] = Field(...)  # Operation method: "set" or "get"
    flowId: str = Field(...)  # Flow identifier for variable scope

    async def async_execute(
        self,
        variable_pool: VariablePool,
        span: Span,
        event_log_node_trace: NodeLog | None = None,
        **kwargs: Any,
    ) -> NodeRunResult:
        """
        Asynchronous execution method for global variable operations.

        Handles both 'set' and 'get' operations:
        - 'set': Stores input variables as global variables in cache
        - 'get': Retrieves global variables and outputs them

        :param variable_pool: Variable pool containing workflow variables
        :param span: Tracing span for monitoring and logging
        :param event_log_node_trace: Optional node logging trace
        :param kwargs: Additional keyword arguments
        :return: NodeRunResult with execution status and data
        """
        try:
            inputs: dict = {}
            outputs: dict = {}

            # Build Redis key components for cache operations
            redis_key = {
                "flow_id": self.flowId,
                "uid": span.uid,
                "app_id": span.app_id,
                "chat_id": variable_pool.chat_id,
            }
            span.add_info_events(
                {"redis_key": json.dumps(redis_key, ensure_ascii=False)}
            )

            # Initialize variable manager for cache operations
            var_manager = VariablesManage(
                flow_id=self.flowId,
                uid=span.uid,
                app_id=span.app_id,
                chat_id=variable_pool.chat_id,
            )
            # Handle 'set' operation: store input variables as global variables
            if self.method == "set":
                for key in self.input_identifier:
                    inputs[key] = variable_pool.get_variable(
                        node_id=self.node_id, key_name=key, span=span
                    )
                    var_manager.add_variable(key, inputs[key])
                span.add_info_events({"set": json.dumps(inputs, ensure_ascii=False)})

            # Handle 'get' operation: retrieve global variables
            elif self.method == "get":
                global_vars = var_manager.get_all_variables()
                for key in self.output_identifier:
                    if key in global_vars:
                        # Use global variable if available
                        outputs.update({key: global_vars.get(key)})
                    else:
                        # Fallback to local variable pool if global variable not found
                        outputs.update(
                            {
                                key: variable_pool.get_variable(
                                    node_id=self.node_id, key_name=key, span=span
                                )
                            }
                        )
                span.add_info_events({"get": json.dumps(outputs, ensure_ascii=False)})
            # Order outputs according to output_identifier sequence
            order_outputs = {}
            for output in self.output_identifier:
                if output in outputs:
                    order_outputs.update({output: outputs.get(output)})
                else:
                    # Fallback to variable pool if output not in processed outputs
                    order_outputs.update(
                        {
                            output: variable_pool.get_variable(
                                node_id=self.node_id, key_name=output, span=span
                            )
                        }
                    )

            # Return successful execution result
            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.SUCCEEDED,
                inputs=inputs,
                outputs=order_outputs,
                node_id=self.node_id,
                node_type=self.node_type,
                alias_name=self.alias_name,
            )
        except Exception as err:
            # Record exception in tracing span and return failed result
            span.record_exception(err)
            return NodeRunResult(
                status=WorkflowNodeExecutionStatus.FAILED,
                error=CustomException(
                    CodeEnum.VARIABLE_NODE_EXECUTION_ERROR,
                    cause_error=err,
                ),
                node_id=self.node_id,
                alias_name=self.alias_name,
                node_type=self.node_type,
            )
