"""API endpoints for executing DDL (Data Definition Language) statements."""

import re
from typing import Any, List

import asyncpg
import asyncpg.exceptions
import sqlglot
from common.otlp.trace.span import Span
from common.service import get_otlp_metric_service, get_otlp_span_service
from fastapi import APIRouter, Depends
from memory.database.api.schemas.exec_ddl_types import ExecDDLInput
from memory.database.api.v1.common import (check_database_exists_by_did_uid,
                                           check_space_id_and_get_uid)
from memory.database.domain.entity.general import exec_sql_statement
from memory.database.domain.entity.schema import set_search_path_by_schema
from memory.database.domain.entity.views.http_resp import format_response
from memory.database.exceptions.error_code import CodeEnum
from memory.database.repository.middleware.getters import get_session
from memory.database.utils.exception_util import unwrap_cause
from sqlglot.errors import ParseError
from sqlglot.expressions import Alter, Command, Create, Drop
from sqlmodel.ext.asyncio.session import AsyncSession
from starlette.responses import JSONResponse

exec_ddl_router = APIRouter(tags=["EXEC_DDL"])

ALLOWED_DDL_STATEMENTS = {
    "CREATE TABLE",
    "ALTER TABLE",
    "DROP TABLE",
    "DROP DATABASE",
    "COMMENT",
    "RENAME",
}


def is_ddl_allowed(sql: str, span_context: Span) -> bool:
    """
    Check if the DDL statement is allowed.

    Args:
        sql: SQL statement to check
        span_context: Span context for tracing

    Returns:
        bool: True if DDL is allowed, False otherwise
    """
    try:
        span_context.add_info_event(f"sql: {sql}")
        parsed = sqlglot.parse_one(sql, error_level="raise")
        statement_type = parsed.key.upper() if parsed.key else ""

        if isinstance(parsed, Drop):
            object_type = parsed.args.get("kind", "").upper()
            full_type = f"DROP {object_type}"
        elif isinstance(parsed, Create):
            object_type = parsed.args.get("kind", "").upper()
            full_type = f"CREATE {object_type}"
        elif isinstance(parsed, Alter):
            object_type = parsed.args.get("kind", "").upper()
            full_type = f"ALTER {object_type}"
        elif isinstance(parsed, Command):
            match = re.search(r"\bALTER\s+TABLE\b", sql, re.IGNORECASE)
            if match:
                full_type = match.group(0).upper()
            else:
                full_type = statement_type
        else:
            full_type = statement_type

        return full_type in ALLOWED_DDL_STATEMENTS

    except ParseError as parse_error:
        span_context.record_exception(parse_error)
        return False


async def _execute_ddl_statements(
    db: Any, schema_list: List[Any], ddls: List[str], span_context: Any
) -> None:
    """Execute DDL statements across all schemas."""
    for schema in schema_list:
        span_context.add_info_event(f"set search path: SET search_path = '{schema[0]}'")
        await set_search_path_by_schema(db, schema[0])
        for statement in ddls:
            try:
                await exec_sql_statement(db, statement)
                span_context.add_info_event(f"exec ddl: {statement}")
            except Exception as exec_error:
                span_context.add_error_event(f"Unsupported syntax, {statement}")
                raise exec_error


async def _handle_ddl_error(
    ddl_error: Exception, db: Any, m: Any, uid: str, span_context: Any
) -> Any:
    """Handle DDL execution errors."""
    span_context.record_exception(ddl_error)
    await db.rollback()
    m.in_error_count(
        CodeEnum.DDLExecutionError.code, lables={"uid": uid}, span=span_context
    )
    root_exc = unwrap_cause(ddl_error)
    if isinstance(root_exc, asyncpg.exceptions.DatatypeMismatchError):
        return format_response(  # type: ignore[no-any-return]
            code=CodeEnum.DDLExecutionError.code,
            message=f"Data type mismatch error, reason: {str(root_exc)}",
            sid=span_context.sid,
        )
    return format_response(
        code=CodeEnum.DDLExecutionError.code,
        message=f"DDL statement execution failed, reason: {str(root_exc)}",
        sid=span_context.sid,
    )


@exec_ddl_router.post("/exec_ddl", response_class=JSONResponse)
async def exec_ddl(
    ddl_input: ExecDDLInput, db: AsyncSession = Depends(get_session)
) -> JSONResponse:
    """
    Execute DDL statements on specified database.

    Args:
        ddl_input: Input containing DDL statements and metadata
        db: Database session

    Returns:
        JSONResponse: Result of DDL execution
    """
    uid = ddl_input.uid
    database_id = ddl_input.database_id
    metric_service = get_otlp_metric_service()
    m = metric_service.get_meter()(func="exec_ddl")
    span_service = get_otlp_span_service()
    span = span_service.get_span()(uid=uid)

    with span.start(
        func_name="exec_ddl",
        add_source_function_name=True,
        attributes={"uid": uid, "database_id": database_id},
    ) as span_context:
        ddl = ddl_input.ddl
        space_id = ddl_input.space_id
        need_check = {
            "database_id": database_id,
            "uid": uid,
            "ddl": ddl,
            "space_id": space_id,
        }
        span_context.add_info_events(need_check)
        span_context.add_info_event(f"database_id: {database_id}")
        span_context.add_info_event(f"uid: {uid}")

        uid, error_reset = await _reset_uid(
            db, database_id, space_id, uid, span_context, m
        )
        if error_reset:
            return error_reset  # type: ignore[no-any-return]

        schema_list, error_resp = await check_database_exists_by_did_uid(
            db, database_id, uid, span_context, m
        )
        if error_resp:
            return error_resp  # type: ignore[no-any-return]

        ddls, error_split = await _ddl_split(ddl, uid, span_context, m)
        if error_split:
            return error_split  # type: ignore[no-any-return]

        try:
            await _execute_ddl_statements(db, schema_list, ddls, span_context)  # type: ignore[arg-type]
            await db.commit()
            m.in_success_count(lables={"uid": uid})
            return format_response(  # type: ignore[no-any-return]
                CodeEnum.Successes.code,
                message=CodeEnum.Successes.msg,
                sid=span_context.sid,
            )
        except Exception as ddl_error:  # pylint: disable=broad-except
            return await _handle_ddl_error(ddl_error, db, m, uid, span_context)  # type: ignore[no-any-return]


async def _reset_uid(
    db: Any, database_id: int, space_id: str, uid: str, span_context: Any, m: Any
) -> Any:
    """Reset UID based on space ID if provided."""
    new_uid = uid

    if space_id:
        create_uid_res, error = await check_space_id_and_get_uid(
            db, database_id, space_id, span_context, m
        )
        if error:
            return None, error

        cur = create_uid_res[0][0]
        if not isinstance(cur, str):
            new_uid = str(cur)

    return new_uid, None


async def _ddl_split(ddl: str, uid: str, span_context: Any, m: Any) -> Any:
    """Split DDL statements and validate them."""
    ddl = ddl.strip()
    ddls = [statement for statement in ddl.split(";") if statement]
    span_context.add_info_event(f"Split DDL statements: {ddls}")

    for statement in ddls:
        if not is_ddl_allowed(statement, span_context):
            span_context.add_error_event(f"invalid ddl: {statement}")
            m.in_error_count(
                CodeEnum.DDLNotAllowed.code, lables={"uid": uid}, span=span_context
            )
            return None, format_response(
                CodeEnum.DDLNotAllowed.code,
                message=f"DDL statement is invalid, illegal statement: {statement}",
                sid=span_context.sid,
            )

    return ddls, None
