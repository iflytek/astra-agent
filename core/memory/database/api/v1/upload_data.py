"""API endpoints for uploading data to database tables."""

import io
from typing import Dict, List, Tuple

import pandas as pd
from common.otlp.trace.span import Span
from common.service import get_otlp_metric_service, get_otlp_span_service
from fastapi import APIRouter, Depends, File, Form, UploadFile
from memory.database.api.schemas.upload_data_types import UploadDataInput
from memory.database.domain.entity.views.http_resp import format_response
from memory.database.exceptions.e import CustomException
from memory.database.exceptions.error_code import CodeEnum
from memory.database.repository.middleware.getters import get_session
from memory.database.utils.snowfake import get_id
from sqlalchemy import text
from sqlmodel.ext.asyncio.session import AsyncSession
from starlette.responses import JSONResponse

upload_data_router = APIRouter(tags=["UPLOAD_DATA"])

SUPPORT_DATA_FILE_TYPES = ("csv", "xls", "xlsx")
INSERT_EXTRA_COLUMNS = ["id", "uid"]


async def parse_upload_file(
    file: UploadFile,
) -> Tuple[List[str], List[Dict], List[int]]:
    """
    Parse the uploaded file and return columns, records and line numbers.

    Args:
        file: Uploaded file to parse

    Returns:
        Tuple containing:
        - List of column names
        - List of record dictionaries
        - List of line numbers

    Raises:
        CustomException: If file type is not supported or parsing fails
    """
    content = await file.read()

    if not file.filename or not file.filename.endswith(SUPPORT_DATA_FILE_TYPES):
        raise CustomException(
            CodeEnum.UploadFileTypeError.code,
            message="Data file type only supports csv, xls or xlsx",
        )

    ext = file.filename.lower().split(".")[-1]
    try:
        if ext == "csv":
            df = pd.read_csv(io.BytesIO(content))
        elif ext in ["xls", "xlsx"]:
            df = pd.read_excel(io.BytesIO(content))
    except Exception as parse_error:  # pylint: disable=broad-except
        raise CustomException(
            CodeEnum.ParseFileError.code,
            message=f"File parsing failed: {str(parse_error)}",
        ) from parse_error

    if df.empty:
        raise CustomException(CodeEnum.FileEmptyError.code, message="File is empty")

    columns = df.columns.tolist()
    records = df.to_dict(orient="records")
    line_numbers = [i + 2 for i in df.index.to_list()]

    return columns, records, line_numbers


async def insert_in_batches(
    db: AsyncSession,
    table_name: str,
    records: List[Dict],
    line_numbers: List[int],
    uid: str,
    batch_size: int = 500,
    span_context: Span = None,
) -> Tuple[List[int], List[Dict]]:
    """
    Insert records into database table in batches.

    Args:
        db: Database session
        table_name: Target table name
        records: List of records to insert
        line_numbers: Corresponding line numbers
        uid: User ID
        batch_size: Batch size for insertion
        span_context: Span context for tracing

    Returns:
        Tuple containing:
        - List of successfully inserted row IDs
        - List of failed rows with error details
    """
    if not records:
        return [], []

    keys = list(records[0].keys())
    keys.extend(INSERT_EXTRA_COLUMNS)
    columns = ", ".join(f'"{k}"' for k in keys)
    placeholders = ", ".join(f":{k}" for k in keys)
    sql_text = f'INSERT INTO "{table_name}" ({columns}) VALUES ({placeholders})'
    sql = text(sql_text)

    if span_context:
        span_context.add_info_events({"insert_in_batches exec sql": sql_text})

    success_rows = []
    failed_rows = []

    for i in range(0, len(records), batch_size):
        batch = records[i : i + batch_size]
        batch_lines = line_numbers[i : i + batch_size]

        for item, line_no in zip(batch, batch_lines):
            try:
                row_id = get_id()
                item.update({"id": row_id, "uid": uid})
                await db.execute(sql, item)  # type: ignore[call-overload]
                success_rows.append(row_id)
            except Exception as insert_error:  # pylint: disable=broad-except
                failed_rows.append({"line": line_no, "error": str(insert_error)})

    return success_rows, failed_rows


@upload_data_router.post("/upload_data", response_class=JSONResponse)
async def upload_data(
    app_id: str = Form(...),
    database_id: int = Form(...),
    uid: str = Form(...),
    table_name: str = Form(...),
    env: str = Form(...),
    file: UploadFile = File(
        ..., description="Upload data file, supports csv or xlsx format"
    ),
    db: AsyncSession = Depends(get_session),
) -> JSONResponse:
    """
    Upload data from file to specified database table.

    Args:
        app_id: Application ID
        database_id: Database ID
        uid: User ID
        table_name: Target table name
        env: Environment (prod/test)
        file: Uploaded data file
        db: Database session

    Returns:
        JSON response with upload results
    """
    metric_service = get_otlp_metric_service()
    m = metric_service.get_meter()(func="upload_data")
    span_service = get_otlp_span_service()
    span = span_service.get_span()(uid=uid)
    with span.start(
        func_name="upload_data",
        add_source_function_name=True,
        attributes={"uid": uid, "database_id": database_id},
    ) as span_context:
        try:
            need_check = UploadDataInput(
                app_id=app_id,
                database_id=database_id,
                uid=uid,
                table_name=table_name,
                env=env,
            )
            span_context.add_info_events(need_check)

            schema = f"{env}_{uid}_{database_id}"
            span_context.add_info_event(f"schema: {schema}")
            try:
                await db.execute(text(f'SET search_path TO "{schema}"'))  # type: ignore[call-overload]
            except Exception as schema_error:  # pylint: disable=broad-except
                span_context.record_exception(schema_error)
                raise CustomException(
                    CodeEnum.NoSchemaError, err_msg=f"Invalid schema: {schema}"
                ) from schema_error

            sql = text(
                """
                SELECT column_name
                FROM information_schema.columns
                WHERE table_name = :table_name AND table_schema = :table_schema
            """
            )
            result = await db.execute(  # type: ignore[call-overload]
                sql, {"table_name": table_name, "table_schema": schema}
            )
            table_columns = [row[0] for row in result.fetchall()]

            columns, records, line_numbers = await parse_upload_file(file)

            span_context.add_info_event(f"upload file columns: {columns}")
            span_context.add_info_event(f"target table columns: {table_columns}")

            diff = set(columns) - set(table_columns)
            if diff:
                raise CustomException(
                    CodeEnum.UploadFileTypeError,
                    err_msg="Upload data column names do not match target table, "
                    "please check",
                )

            success_rows, failed_rows = await insert_in_batches(
                db, table_name, records, line_numbers, uid, span_context=span_context
            )

            span_context.add_info_event(f"insert successful rows: {success_rows}")
            span_context.add_info_event(f"insert failing rows: {failed_rows}")

            try:
                await db.commit()
            except Exception as commit_error:  # pylint: disable=broad-except
                await db.rollback()
                raise CustomException(
                    CodeEnum.DatabaseExecutionError,
                    err_msg=f"Execution failed: {str(commit_error)}",
                ) from commit_error

            m.in_success_count(lables={"uid": uid})
            return format_response(  # type: ignore[no-any-return]
                0,
                message="success",
                data={"success_rows": success_rows, "failed_rows": failed_rows},
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
                code=-1,
                lables={"uid": uid},
                span=span_context,
            )
            return format_response(  # type: ignore[no-any-return]
                code="-1", message="Upload data failed", sid=span_context.sid
            )
