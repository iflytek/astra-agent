"""API endpoints for exporting data from database tables to CSV format."""

import csv
import io
from typing import Any, Generator, Union

from common.otlp.metrics.meter import Meter
from common.otlp.trace.span import Span
from common.service import get_otlp_metric_service, get_otlp_span_service
from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse
from memory.database.api.schemas.export_data_types import ExportDataInput
from memory.database.domain.entity.views.http_resp import format_response
from memory.database.exceptions.e import CustomException
from memory.database.exceptions.error_code import CodeEnum
from memory.database.repository.middleware.getters import get_session
from sqlalchemy import text
from sqlmodel.ext.asyncio.session import AsyncSession
from starlette.responses import JSONResponse

export_data_router = APIRouter(tags=["EXPORT_DATA"])


@export_data_router.post(
    "/export_data", response_class=JSONResponse, response_model=None
)
async def export_data(
    export_input: ExportDataInput, db: AsyncSession = Depends(get_session)
) -> Union[JSONResponse, StreamingResponse]:
    """
    Export data from specified database table to CSV format.

    Args:
        export_input: Input parameters for data export
        db: Database session

    Returns:
        StreamingResponse: CSV file download response
    """
    app_id = export_input.app_id
    uid = export_input.uid
    database_id = export_input.database_id
    table_name = export_input.table_name
    env = export_input.env

    metric_service = get_otlp_metric_service()
    m = metric_service.get_meter()(func="export_data")
    span_service = get_otlp_span_service()
    span = span_service.get_span()(uid=uid)

    with span.start(
        func_name="export_data",
        add_source_function_name=True,
        attributes={"uid": uid, "database_id": database_id},
    ) as span_context:
        try:
            need_check = {
                "app_id": app_id,
                "database_id": database_id,
                "uid": uid,
                "table_name": table_name,
                "env": env,
            }
            span_context.add_info_events(need_check)

            rows, columns, error_response = await _set_search_path_and_exec(
                db, database_id, table_name, env, uid, span_context, m
            )
            if error_response:
                return error_response  # type: ignore[no-any-return]

            def generate_csv() -> Generator[str, None, None]:
                """Generate CSV data from query results."""
                stream = io.StringIO()
                writer = csv.writer(stream)
                writer.writerow(columns)
                for row in rows:
                    writer.writerow([str(v) if v is not None else "" for v in row])
                stream.seek(0)
                yield stream.read()

            filename = f"{table_name}_export.csv"
            m.in_success_count(lables={"uid": uid})
            return StreamingResponse(
                generate_csv(),
                media_type="text/csv",
                headers={"Content-Disposition": f"attachment; filename={filename}"},
            )
        except CustomException as custom_error:
            m.in_error_count(custom_error.code, lables={"uid": uid}, span=span_context)
            return format_response(  # type: ignore[no-any-return]
                code=custom_error.code,
                message=custom_error.message,
                sid=span_context.sid,
            )
        except Exception as unexpected_error:  # pylint: disable=broad-except
            span_context.record_exception(unexpected_error)
            m.in_error_count(
                CodeEnum.DatabaseExecutionError.code,
                lables={"uid": uid},
                span=span_context,
            )
            return format_response(  # type: ignore[no-any-return]
                code="-1", message="Export data failed", sid=span_context.sid
            )


async def _set_search_path_and_exec(
    db: AsyncSession,
    database_id: int,
    table_name: str,
    env: str,
    uid: str,
    span_context: Span,
    m: Meter,
) -> tuple:
    """
    Set search path and execute query to fetch data.

    Args:
        db: Database session
        database_id: Database ID
        table_name: Table name to export
        env: Environment (prod/test)
        uid: User ID
        span_context: Span context for tracing
        m: Meter for metrics

    Returns:
        tuple: (rows, columns, error_response)
    """
    schema = f"{env}_{uid}_{database_id}"
    span_context.add_info_event(f"schema: {schema}")

    try:
        await db.execute(text(f'SET search_path TO "{schema}"'))  # type: ignore[call-overload]
    except Exception as schema_error:  # pylint: disable=broad-except
        span_context.record_exception(schema_error)
        m.in_error_count(
            CodeEnum.NoSchemaError.code, lables={"uid": uid}, span=span_context
        )
        return (
            None,
            None,
            format_response(
                code=CodeEnum.NoSchemaError.code,
                message=f"Invalid schema: {schema}",
                sid=span_context.sid,
            ),
        )

    try:
        result = await db.execute(  # type: ignore[call-overload]
            text(f'SELECT * FROM "{table_name}" WHERE uid = :uid'), {"uid": uid}
        )
        rows = result.fetchall()
        columns = result.keys()
    except Exception as query_error:  # pylint: disable=broad-except
        span_context.record_exception(query_error)
        m.in_error_count(
            CodeEnum.DatabaseExecutionError.code,
            lables={"uid": uid},
            span=span_context,
        )
        return (
            None,
            None,
            format_response(
                code=CodeEnum.DatabaseExecutionError.code,
                message=f"Data query failed: {str(query_error)}",
                sid=span_context.sid,
            ),
        )

    if not rows:
        span_context.add_info_event(f"No data in {schema}.{table_name} table")

    return rows, columns, None
