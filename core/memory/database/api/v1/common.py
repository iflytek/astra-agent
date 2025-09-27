"""
Database operator API endpoints
for common databases.
"""

from typing import Any, List, Optional, Tuple

import sqlalchemy
import sqlalchemy.exc
from memory.database.domain.entity.database_meta import (
    get_id_by_did, get_id_by_did_uid, get_uid_by_did_space_id)
from memory.database.domain.entity.schema_meta import get_schema_name_by_did
from memory.database.domain.entity.views.http_resp import format_response
from memory.database.exceptions.error_code import CodeEnum


async def check_database_exists_by_did_uid(
    db: Any, database_id: int, uid: str, span_context: Any, m: Any
) -> Tuple[Optional[List[List[str]]], Optional[Any]]:
    """Check if database exists and return its schemas."""
    try:
        db_id_res = await get_id_by_did_uid(db, database_id=database_id, uid=uid)
        if not db_id_res:
            m.in_error_count(
                CodeEnum.DatabaseNotExistError.code,
                lables={"uid": uid},
                span=span_context,
            )
            span_context.add_error_event(
                f"User: {uid} does not have database: {database_id}"
            )
            return None, format_response(
                code=CodeEnum.DatabaseNotExistError.code,
                message=f"uid: {uid} or database_id: {database_id} error, "
                "please verify",
                sid=span_context.sid,
            )

        res = await get_schema_name_by_did(db, database_id=database_id)
        if not res:
            return None, format_response(
                code=CodeEnum.DatabaseNotExistError.code,
                message=CodeEnum.DatabaseNotExistError.msg,
                sid=span_context.sid,
            )
        return res, None
    except sqlalchemy.exc.DBAPIError as e:
        await db.rollback()
        span_context.record_exception(e)
        m.in_error_count(
            CodeEnum.DatabaseExecutionError.code, lables={"uid": uid}, span=span_context
        )
        return None, format_response(
            code=CodeEnum.DatabaseExecutionError.code,
            message=f"Database execution failed. Please check if the passed "
            f"database id and uid are correct, {str(e.__cause__)}",
            sid=span_context.sid,
        )
    except Exception as e:  # pylint: disable=broad-except
        span_context.report_exception(e)
        m.in_error_count(
            CodeEnum.DatabaseExecutionError.code, lables={"uid": uid}, span=span_context
        )
        return None, format_response(
            code=CodeEnum.DatabaseExecutionError.code,
            message=f"{str(e.__cause__)}",
            sid=span_context.sid,
        )


async def check_database_exists_by_did(
    db: Any, database_id: int, uid: str, span_context: Any, m: Any
) -> Tuple[Optional[List[List[str]]], Optional[Any]]:
    """Check if database exists."""
    try:
        db_id_res = await get_id_by_did(db, database_id)
        if not db_id_res:
            m.in_error_count(
                CodeEnum.DatabaseNotExistError.code,
                lables={"uid": uid},
                span=span_context,
            )
            span_context.add_error_event(f"Database does not exist: {database_id}")
            return None, format_response(
                code=CodeEnum.DatabaseNotExistError.code,
                message=f"database_id: {database_id} error, please verify",
                sid=span_context.sid,
            )

        res = await get_schema_name_by_did(db, database_id)
        if not res:
            m.in_error_count(
                CodeEnum.DatabaseNotExistError.code,
                lables={"uid": uid},
                span=span_context,
            )
            return None, format_response(
                code=CodeEnum.DatabaseNotExistError.code,
                message=CodeEnum.DatabaseNotExistError.msg,
                sid=span_context.sid,
            )
        return res, None

    except Exception as db_error:
        span_context.record_exception(db_error)
        m.in_error_count(
            CodeEnum.DatabaseExecutionError.code,
            lables={"uid": uid},
            span=span_context,
        )
        return None, format_response(
            code=CodeEnum.DatabaseExecutionError.code,
            message="Database execution failed",
            sid=span_context.sid,
        )


async def check_space_id_and_get_uid(
    db: Any, database_id: int, space_id: str, span_context: Any, m: Any
) -> Tuple[Optional[List[List[str]]], Optional[Any]]:
    """Check if space ID is valid."""
    span_context.add_info_event(f"space_id: {space_id}")
    create_uid_res = await get_uid_by_did_space_id(db, database_id, space_id)
    if not create_uid_res:
        m.in_error_count(
            CodeEnum.SpaceIDNotExistError.code,
            lables={"space_id": space_id},
            span=span_context,
        )
        span_context.add_error_event(
            f"space_id: {space_id} does not contain database_id: {database_id}"
        )
        return None, format_response(
            code=CodeEnum.SpaceIDNotExistError.code,
            message=f"space_id: {space_id} does not contain database_id: {database_id}",
            sid=span_context.sid,
        )

    return create_uid_res, None
