"""
Node debugging API endpoints.

This module provides API endpoints for debugging workflow nodes,
including code execution and node-specific debugging functionality.
"""

import json

from fastapi import APIRouter
from starlette.responses import JSONResponse

from workflow.domain.entities.node_debug_vo import CodeRunVo, NodeDebugVo
from workflow.domain.entities.response import Resp
from workflow.engine.entities.node_entities import NodeType
from workflow.engine.nodes.code.code_node import CodeNode
from workflow.exception.e import CustomException
from workflow.exception.errors.err_code import CodeEnum
from workflow.extensions.otlp.metric.meter import Meter
from workflow.extensions.otlp.trace.span import Span
from workflow.service import flow_service

router = APIRouter(tags=["code_debug"])


@router.post("/run", status_code=200)  # Legacy interface compatibility
@router.post("/code/run", status_code=200)
async def run_code(code_run_vo: CodeRunVo) -> JSONResponse:
    """
    Execute code node code
    :param code_run_vo: Code run request data
    :return: Execution result
    """
    m = Meter(app_id=code_run_vo.app_id)
    span = Span()
    with span.start(attributes={"flow_id": code_run_vo.flow_id}) as span_context:
        span.add_info_events(
            {"inputs": json.dumps(code_run_vo.dict(), ensure_ascii=False)}
        )
        var_dict = {}

        try:

            for var in code_run_vo.variables:
                var_dict.update({var.name: var.content})

            cn = CodeNode(
                codeLanguage="python",
                input_identifier=[],
                output_identifier=[],
                code=code_run_vo.code,
                appId=code_run_vo.app_id,
                uid=code_run_vo.uid,
            )
            data = await cn.execute_code(var_dict, span_context)

        except CustomException as err:
            span_context.record_exception(err)
            m.in_error_count(err.code, span=span_context)
            return Resp.error(code=err.code, message=err.message, sid=span.sid)
        except Exception as err:
            span_context.record_exception(err)
            m.in_error_count(CodeEnum.CODE_EXECUTION_ERROR.code, span=span_context)
            return Resp.error(
                code=CodeEnum.CODE_EXECUTION_ERROR.code, message=str(err), sid=span.sid
            )

        m.in_success_count()
        return Resp.success(data, span.sid)


@router.post("/node/debug", status_code=200)
async def node_debug(node_debug_vo: NodeDebugVo) -> JSONResponse:
    """
    Debug a node in the workflow
    :param node_debug_vo: Node debug request data
    :return: Debug execution result
    """
    m = Meter()
    span = Span()
    with span.start(attributes={"flow_id": node_debug_vo.id}) as span_context:
        try:
            if node_debug_vo.data.nodes[0].id.split("::")[0] in NodeType.DATABASE.value:
                span.uid = node_debug_vo.data.nodes[0].data.nodeParam.get("uid", "")
            node_debug_resp_vo = await flow_service.node_debug(
                node_debug_vo.data, span_context
            )

        except CustomException as err:
            m.in_error_count(err.code, span=span_context)
            span.record_exception(err)
            return Resp.error(code=err.code, message=err.message, sid=span.sid)
        except Exception as err:
            m.in_error_count(CodeEnum.NODE_DEBUG_ERROR.code, span=span_context)
            span.record_exception(err)
            return Resp.error(
                code=CodeEnum.NODE_DEBUG_ERROR.code, message=str(err), sid=span.sid
            )
        m.in_success_count()
        span_context.add_info_events(
            {
                "node_debug_resp": json.dumps(
                    node_debug_resp_vo.dict(), ensure_ascii=False
                )
            }
        )
        return Resp.success(node_debug_resp_vo.dict(), span.sid)
