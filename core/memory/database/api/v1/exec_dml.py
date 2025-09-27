"""API endpoints for executing DML (Data Manipulation Language) statements."""

import datetime
import decimal
import re
import time
import uuid
from typing import Any, List

import sqlparse
from common.otlp.trace.span import Span
from common.service import get_otlp_metric_service, get_otlp_span_service
from fastapi import APIRouter, Depends
from memory.database.api.schemas.exec_dml_types import ExecDMLInput
from memory.database.api.v1.common import (check_database_exists_by_did,
                                           check_space_id_and_get_uid)
from memory.database.domain.entity.general import (exec_sql_statement,
                                                   parse_and_exec_sql)
from memory.database.domain.entity.schema import set_search_path_by_schema
from memory.database.domain.entity.views.http_resp import format_response
from memory.database.exceptions.e import CustomException
from memory.database.exceptions.error_code import CodeEnum
from memory.database.repository.middleware.getters import get_session
from memory.database.utils.snowfake import get_id
from sqlglot import exp, parse_one
from sqlmodel.ext.asyncio.session import AsyncSession
from starlette.responses import JSONResponse

exec_dml_router = APIRouter(tags=["EXEC_DML"])

INSERT_EXTRA_COLUMNS = ["id", "uid", "create_time", "update_time"]


def rewrite_dml_with_uid_and_limit(
    dml: str,
    app_id: str,
    uid: str,
    limit_num: int,
    env: str,  # pylint: disable=unused-argument
    span_context: Span,  # pylint: disable=unused-argument
) -> tuple[str, list]:
    """
    Rewrite DML with UID and limit expressions.

    Args:
        dml: Original DML statement
        app_id: Application ID
        uid: User ID
        limit_num: Limit number for SELECT queries
        env: Environment (prod/test)
        span_context: Span context for tracing

    Returns:
        tuple: (rewritten_sql, insert_ids)
    """
    parsed = parse_one(dml)
    insert_ids: List[int] = []

    tables = [table.alias_or_name for table in parsed.find_all(exp.Table)]

    if isinstance(parsed, (exp.Update, exp.Delete, exp.Select)):
        _dml_add_where(parsed, tables, app_id, uid)

    if isinstance(parsed, exp.Select):
        limit = parsed.args.get("limit")
        if not limit:
            parsed.set("limit", exp.Limit(expression=exp.Literal.number(limit_num)))

    if isinstance(parsed, exp.Insert):
        _dml_insert_add_params(parsed, insert_ids, app_id, uid)

    return parsed.sql(dialect="postgres"), insert_ids


def _dml_add_where(parsed: Any, tables: List[str], app_id: str, uid: str) -> None:
    """Add WHERE conditions to DML statements."""
    where_expr = parsed.args.get("where")
    uid_conditions = []

    for table in tables:
        uid_col = exp.Column(this="uid", table=table)
        condition = exp.In(
            this=uid_col,
            expressions=[
                exp.Literal.string(f"{uid}"),
                exp.Literal.string(f"{app_id}:{uid}"),
            ],
        )
        uid_conditions.append(condition)

    final_condition = uid_conditions[0]
    for cond in uid_conditions[1:]:
        final_condition = exp.and_(final_condition, cond)  # type: ignore[assignment]

    if where_expr:
        grouped_where = exp.Paren(this=where_expr.this)
        new_where = exp.and_(grouped_where, final_condition)
    else:
        new_where = final_condition

    parsed.set("where", exp.Where(this=new_where))


def _dml_insert_add_params(
    parsed: Any, insert_ids: List[int], app_id: str, uid: str
) -> None:
    """Add parameters to INSERT statements."""
    existing_columns = parsed.args["this"].expressions or []
    insert_exprs = parsed.args["expression"]
    rows = insert_exprs.expressions

    extra_fields = ["id", "uid"]

    need_del_index = []
    for index, column in enumerate(existing_columns):
        if column.this in INSERT_EXTRA_COLUMNS:
            need_del_index.append(index)

    need_del_index.reverse()
    for index in need_del_index:
        existing_columns.pop(index)
        for row in rows:
            row.expressions.pop(index)

    for name in extra_fields:
        existing_columns.append(exp.to_identifier(name))

    for i, row in enumerate(rows):
        row_id = get_id()
        insert_ids.append(row_id)
        extra_values = [
            exp.Literal.number(row_id),
            exp.Literal.string(f"{app_id}:{uid}"),
        ]
        new_exprs = list(row.expressions) + [val.copy() for val in extra_values]
        rows[i] = exp.Tuple(expressions=new_exprs)

    parsed.set("columns", exp.Tuple(this=existing_columns))
    parsed.set("expression", insert_exprs)


def to_jsonable(obj: Any) -> Any:
    """Convert object to JSON-serializable format."""
    if isinstance(obj, dict):
        return {k: to_jsonable(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple, set)):
        return [to_jsonable(item) for item in obj]
    if isinstance(obj, datetime.datetime):
        return obj.isoformat(sep=" ", timespec="seconds")
    if isinstance(obj, datetime.date):
        return obj.isoformat()
    if isinstance(obj, decimal.Decimal):
        return float(obj)
    if isinstance(obj, uuid.UUID):
        return str(obj)
    return obj


async def _validate_and_prepare_dml(
    db: Any, dml_input: Any, span_context: Any, m: Any
) -> Any:
    """Validate input and prepare DML execution."""
    app_id = dml_input.app_id
    uid = dml_input.uid
    database_id = dml_input.database_id
    dml = dml_input.dml
    env = dml_input.env
    space_id = dml_input.space_id

    need_check = {
        "app_id": app_id,
        "database_id": database_id,
        "uid": uid,
        "dml": dml,
        "env": env,
        "space_id": space_id,
    }
    span_context.add_info_events(need_check)
    span_context.add_info_event(f"app_id: {app_id}")
    span_context.add_info_event(f"database_id: {database_id}")
    span_context.add_info_event(f"uid: {uid}")

    if space_id:
        _, error_spaceid = await check_space_id_and_get_uid(
            db, database_id, space_id, span_context, m
        )
        if error_spaceid:
            return None, error_spaceid

    schema_list, error_resp = await check_database_exists_by_did(
        db, database_id, uid, span_context, m
    )
    if error_resp:
        return None, error_resp

    return (app_id, uid, database_id, dml, env, schema_list), None


async def _process_dml_statements(
    dmls: List[str], app_id: str, uid: str, env: str, span_context: Any
) -> Any:
    """Process and rewrite DML statements."""
    rewrite_dmls = []
    for statement in dmls:
        rewrite_dml, insert_ids = rewrite_dml_with_uid_and_limit(
            dml=statement,
            app_id=app_id,
            uid=uid,
            limit_num=100,
            env=env,
            span_context=span_context,
        )
        span_context.add_info_event(f"rewrite dml: {rewrite_dml}")
        rewrite_dmls.append(
            {
                "rewrite_dml": rewrite_dml,
                "insert_ids": insert_ids,
            }
        )
    return rewrite_dmls


@exec_dml_router.post("/exec_dml", response_class=JSONResponse)
async def exec_dml(
    dml_input: ExecDMLInput, db: AsyncSession = Depends(get_session)
) -> JSONResponse:
    """
    Execute DML statements on specified database.

    Args:
        dml_input: Input containing DML statements and metadata
        db: Database session

    Returns:
        JSONResponse: Result of DML execution
    """
    uid = dml_input.uid
    database_id = dml_input.database_id
    metric_service = get_otlp_metric_service()
    m = metric_service.get_meter()(func="exec_dml")
    span_service = get_otlp_span_service()
    span = span_service.get_span()(uid=uid)

    with span.start(
        func_name="exec_dml",
        add_source_function_name=True,
        attributes={"uid": uid, "database_id": database_id},
    ) as span_context:
        try:
            validated_data, error = await _validate_and_prepare_dml(
                db, dml_input, span_context, m
            )
            if error:
                return error  # type: ignore[no-any-return]

            app_id, uid, database_id, dml, env, schema_list = validated_data

            schema, error_search = await _set_search_path(
                db, schema_list, env, uid, span_context, m
            )
            if error_search:
                return error_search  # type: ignore[no-any-return]

            dmls, error_split = await _dml_split(dml, db, schema, uid, span_context, m)
            if error_split:
                return error_split  # type: ignore[no-any-return]

            rewrite_dmls = await _process_dml_statements(
                dmls, app_id, uid, env, span_context
            )

            final_exec_success_res, exec_time, error_exec = await _exec_dml_sql(
                db, rewrite_dmls, uid, span_context, m
            )
            if error_exec:
                return error_exec  # type: ignore[no-any-return]

            return format_response(  # type: ignore[no-any-return]
                CodeEnum.Successes.code,
                message=CodeEnum.Successes.msg,
                sid=span_context.sid,
                data={
                    "exec_success": final_exec_success_res,
                    "exec_failure": [],
                    "exec_time": exec_time,
                },
            )
        except CustomException as custom_error:
            span_context.record_exception(custom_error)
            m.in_error_count(custom_error.code, lables={"uid": uid}, span=span_context)
            return format_response(  # type: ignore[no-any-return]
                code=custom_error.code,
                message="Database execution failed",
                sid=span_context.sid,
            )
        except Exception as unexpected_error:  # pylint: disable=broad-except
            m.in_error_count(
                CodeEnum.DMLExecutionError.code, lables={"uid": uid}, span=span_context
            )
            span_context.record_exception(unexpected_error)
            return format_response(  # type: ignore[no-any-return]
                code=CodeEnum.DMLExecutionError.code,
                message="Database execution failed",
                sid=span_context.sid,
            )


async def _exec_dml_sql(
    db: Any, rewrite_dmls: List[Any], uid: str, span_context: Any, m: Any
) -> Any:
    """Execute rewritten DML SQL statements."""
    final_exec_success_res = []
    start_time = time.time()

    try:
        for dml_info in rewrite_dmls:
            rewrite_dml = dml_info["rewrite_dml"]
            insert_ids = dml_info["insert_ids"]

            result = await exec_sql_statement(db, rewrite_dml)
            try:
                exec_result = result.mappings().all()
                exec_result_dicts = [dict(row) for row in exec_result]
                exec_result_dicts = to_jsonable(exec_result_dicts)
            except Exception as mapping_error:
                span_context.add_info_event(f"{str(mapping_error)}")
                exec_result_dicts = []

            span_context.add_info_event(f"exec result: {exec_result_dicts}")

            if exec_result_dicts:
                final_exec_success_res.extend(exec_result_dicts)
            elif insert_ids:
                final_exec_success_res.extend([{"id": v} for v in insert_ids])

            await db.commit()

        m.in_success_count(lables={"uid": uid})
        exec_time = time.time() - start_time
        return final_exec_success_res, exec_time, None

    except Exception as exec_error:  # pylint: disable=broad-except
        span_context.record_exception(exec_error)
        await db.rollback()
        m.in_error_count(
            CodeEnum.DatabaseExecutionError.code, lables={"uid": uid}, span=span_context
        )
        return (
            None,
            None,
            format_response(
                code=CodeEnum.DatabaseExecutionError.code,
                message="Database execution failed",
                sid=span_context.sid,
            ),
        )


async def _set_search_path(
    db: Any, schema_list: List[Any], env: str, uid: str, span_context: Any, m: Any
) -> Any:
    """Set search path for database operations."""
    schema = next((one[0] for one in schema_list if env in one[0]), "")
    if not schema:
        span_context.add_error_event("Corresponding schema not found")
        m.in_error_count(
            CodeEnum.NoSchemaError.code, lables={"uid": uid}, span=span_context
        )
        return None, format_response(
            code=CodeEnum.NoSchemaError.code,
            message=f"Corresponding schema not found: {schema}",
            sid=span_context.sid,
        )

    span_context.add_info_event(f"schema: {schema}")
    try:
        await set_search_path_by_schema(db, schema)
        return schema, None
    except Exception as schema_error:  # pylint: disable=broad-except
        span_context.record_exception(schema_error)
        m.in_error_count(
            CodeEnum.NoSchemaError.code, lables={"uid": uid}, span=span_context
        )
        return None, format_response(
            code=CodeEnum.NoSchemaError.code,
            message=f"Invalid schema: {schema}",
            sid=span_context.sid,
        )


async def _dml_split(
    dml: str, db: Any, schema: str, uid: str, span_context: Any, m: Any
) -> Any:
    """Split and validate DML statements."""
    dml = dml.strip()
    dmls = sqlparse.split(dml)
    span_context.add_info_event(f"Split DML statements: {dmls}")

    for statement in dmls:
        try:
            parsed = parse_one(statement)
            tables = {table.name for table in parsed.find_all(exp.Table)}
        except Exception as parse_error:  # pylint: disable=broad-except
            span_context.record_exception(parse_error)
            m.in_error_count(
                CodeEnum.SQLParseError.code,
                lables={"uid": uid},
                span=span_context,
            )
            return None, format_response(
                code=CodeEnum.SQLParseError.code,
                message="SQL parsing failed",
                sid=span_context.sid,
            )

        result = await parse_and_exec_sql(
            db,
            "SELECT tablename FROM pg_tables WHERE schemaname = :schema",
            {"schema": schema},
        )
        valid_tables = {row[0] for row in result.fetchall()}
        not_found = tables - valid_tables

        if not_found:
            span_context.add_error_event(
                f"Table does not exist or no permission: {', '.join(not_found)}"
            )
            m.in_error_count(
                CodeEnum.NoAuthorityError.code,
                lables={"uid": uid},
                span=span_context,
            )
            return None, format_response(
                code=CodeEnum.NoAuthorityError.code,
                message=f"Table does not exist or no permission: "
                f"{', '.join(not_found)}",
                sid=span_context.sid,
            )

        allowed_sql = re.compile(r"^\s*(SELECT|INSERT|UPDATE|DELETE)\s+", re.IGNORECASE)
        if not allowed_sql.match(statement):
            span_context.add_error_events({"invalid dml": statement})
            m.in_error_count(
                CodeEnum.DMLNotAllowed.code,
                lables={"uid": uid},
                span=span_context,
            )
            return None, format_response(
                code=CodeEnum.DMLNotAllowed.code,
                message="Unsupported SQL type, only "
                "SELECT/INSERT/UPDATE/DELETE allowed",
                sid=span_context.sid,
            )

    return dmls, None
