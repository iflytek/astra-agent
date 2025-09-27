"""
Database operator API endpoints
for creating, cloning, dropping and modifying databases.
"""

import sqlalchemy
import sqlalchemy.exc
from common.otlp.trace.span import Span
from common.service import get_otlp_metric_service, get_otlp_span_service
from fastapi import APIRouter, Depends
from memory.database.api.schemas.clone_db_types import CloneDBInput
from memory.database.api.schemas.create_db_types import CreateDBInput
from memory.database.api.schemas.drop_db_types import DropDBInput
from memory.database.api.schemas.modify_db_desc_types import ModifyDBDescInput
from memory.database.api.v1.common import check_database_exists_by_did_uid
from memory.database.domain.entity.database_meta import (
    del_database_meta_by_did, get_id_by_did_uid, get_uid_by_did_space_id,
    get_uid_by_space_id, update_database_meta_by_did_uid)
from memory.database.domain.entity.schema_meta import (del_schema_meta_by_did,
                                                       get_schema_name_by_did)
from memory.database.domain.entity.views.http_resp import format_response
from memory.database.domain.models.database_meta import DatabaseMeta
from memory.database.domain.models.schema_meta import SchemaMeta
from memory.database.exceptions.error_code import CodeEnum
from memory.database.repository.middleware.getters import get_session
from memory.database.utils.snowfake import get_id
from pydantic import BaseModel
from sqlalchemy import text
from sqlmodel.ext.asyncio.session import AsyncSession
from starlette.responses import JSONResponse

clone_db_router = APIRouter(tags=["CLONE_DB"])
create_db_router = APIRouter(tags=["CREATE_DB"])
drop_db_router = APIRouter(tags=["DROP_DB"])
modify_db_description_router = APIRouter(tags=["MODIFY_DB_DESC"])


def generate_copy_table_structures_sql(source_schema: str, target_schema: str) -> str:
    """Generate SQL for copying table structures from source to target schema."""
    copy_table_structures_sql = f"""
        DO $$
        DECLARE
            tbl RECORD;
        BEGIN
            FOR tbl IN
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = '{source_schema}'
            LOOP
                EXECUTE format(
                    'CREATE TABLE {target_schema}.%I
                    (LIKE {source_schema}.%I INCLUDING ALL)',
                    tbl.tablename, tbl.tablename
                );
            END LOOP;
        END;
        $$ LANGUAGE plpgsql;
        """
    return copy_table_structures_sql


def generate_copy_data_sql(source_schema: str, target_schema: str) -> str:
    """Generate SQL for copying data from source to target schema."""
    copy_data_sql = f"""
        DO $$
        DECLARE
            tbl RECORD;
        BEGIN
            FOR tbl IN
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = '{source_schema}'
            LOOP
                EXECUTE format(
                    'INSERT INTO {target_schema}.%I SELECT * FROM {source_schema}.%I',
                    tbl.tablename, tbl.tablename
                );
            END LOOP;
        END;
        $$ LANGUAGE plpgsql;
        """
    return copy_data_sql


@clone_db_router.post("/clone_database", response_class=JSONResponse)
async def clone_db(
    clone_input: CloneDBInput, db: AsyncSession = Depends(get_session)
) -> JSONResponse:
    """Clone an existing database with all its schemas and data."""
    database_id = clone_input.database_id
    uid = clone_input.uid
    new_database_name = clone_input.new_database_name
    metric_service = get_otlp_metric_service()
    m = metric_service.get_meter()(func="clone_database")
    span_service = get_otlp_span_service()
    span = span_service.get_span()(uid=uid)
    with span.start(
        func_name="clone_db",
        add_source_function_name=True,
        attributes={"database_id": database_id, "uid": uid},
    ) as span_context:
        need_check = {
            "database_id": database_id,
            "uid": uid,
            "new_database_name": new_database_name,
        }
        span_context.add_info_events(need_check)
        span_context.add_info_event(f"database_id: {database_id}")
        span_context.add_info_event(f"uid: {uid}")

        # Validate database
        _, error_resp = await check_database_exists_by_did_uid(
            db, database_id, uid, span_context, m
        )
        if error_resp:
            return error_resp  # type: ignore[no-any-return]

        try:
            old_database_meta = await db.execute(  # type: ignore[call-overload]
                text(
                    """
                    SELECT uid, name, description FROM database_meta
                    WHERE id=:database_id
                    """
                ),
                {"database_id": database_id},
            )
            old_database_meta = old_database_meta.first()  # type: ignore[assignment]
            old_prod_test_schema_meta = await get_schema_name_by_did(db, database_id)
            uid, old_name, old_description = old_database_meta
            span_context.add_info_events({"old_database_uid": uid})
            span_context.add_info_events({"old_database_name": old_name})
            span_context.add_info_events({"old_database_description": old_description})
            create_db_input = CreateDBInput(
                uid=uid, database_name=new_database_name, description=old_description
            )
            new_database_info = await exec_generate_schema(create_db_input, span, db)
            await db.exec(  # type: ignore[call-overload]
                text(f"CREATE SCHEMA IF NOT EXISTS {new_database_info.prod_schema}")
            )
            await db.exec(  # type: ignore[call-overload]
                text(f"CREATE SCHEMA IF NOT EXISTS {new_database_info.test_schema}")
            )
            for schema in old_prod_test_schema_meta:
                if "prod" in schema[0]:
                    target_schema = new_database_info.prod_schema
                else:
                    target_schema = new_database_info.test_schema
                copy_table_structures_sql = generate_copy_table_structures_sql(
                    source_schema=schema[0], target_schema=target_schema
                )
                copy_data_sql = generate_copy_data_sql(
                    source_schema=schema[0], target_schema=target_schema
                )
                await db.execute(text(copy_table_structures_sql))  # type: ignore[call-overload]
                await db.execute(text(copy_data_sql))  # type: ignore[call-overload]
            await db.commit()
            m.in_success_count(lables={"uid": uid})
            return format_response(  # type: ignore[no-any-return]
                CodeEnum.Successes.code,
                message=CodeEnum.Successes.msg,
                data={"database_id": new_database_info.database_id},
                sid=span_context.sid,
            )
        except sqlalchemy.exc.IntegrityError as e:
            await db.rollback()
            span_context.record_exception(e)
            m.in_error_count(
                CodeEnum.DatabaseExecutionError.code,
                lables={"uid": uid},
                span=span_context,
            )
            return format_response(  # type: ignore[no-any-return]
                CodeEnum.DatabaseExecutionError.code,
                message=f"Database consistency error, {e}",
                sid=span_context.sid,
            )
        except Exception as e:  # pylint: disable=broad-except
            await db.rollback()
            m.in_error_count(
                CodeEnum.HttpError.code, lables={"uid": uid}, span=span_context
            )
            span_context.record_exception(e)
            return format_response(  # type: ignore[no-any-return]
                CodeEnum.HttpError.code, message=str(e), sid=span_context.sid
            )


class DatabaseInfo(BaseModel):
    """Database information model containing ID and schema names."""

    database_id: int
    prod_schema: str
    test_schema: str


async def exec_generate_schema(
    create_input: CreateDBInput, span_context: Span, db: AsyncSession
) -> DatabaseInfo:
    """Execute schema generation for a new database."""
    database_id = get_id()
    uid = create_input.uid
    space_id = create_input.space_id
    try:
        if space_id:
            create_uid = await get_uid_by_space_id(db, space_id)
            if create_uid:
                uid = create_uid[0]

        prod_schema = f"prod_{uid}_{database_id}"
        dev_schema = f"test_{uid}_{database_id}"
        span_context.add_info_event(f"prod_schema: {prod_schema}")
        span_context.add_info_event(f"dev_schema: {dev_schema}")

        await db.exec(text(f'CREATE SCHEMA IF NOT EXISTS "{prod_schema}"'))  # type: ignore[call-overload]
        await db.exec(text(f'CREATE SCHEMA IF NOT EXISTS "{dev_schema}"'))  # type: ignore[call-overload]

        database_info = DatabaseMeta(
            id=database_id,
            uid=uid,
            name=create_input.database_name,
            description=create_input.description,
            space_id=space_id,
        )
        prod_schema_info = SchemaMeta(
            database_id=database_id,
            schema_name=prod_schema,
        )
        dev_schema_info = SchemaMeta(
            database_id=database_id,
            schema_name=dev_schema,
        )
        db.add(database_info)
        db.add(prod_schema_info)
        db.add(dev_schema_info)
        await db.commit()
        return DatabaseInfo(
            database_id=database_id, prod_schema=prod_schema, test_schema=dev_schema
        )
    except Exception as e:  # pylint: disable=broad-except
        raise e


@create_db_router.post("/create_database", response_class=JSONResponse)
async def create_db(
    create_input: CreateDBInput, db: AsyncSession = Depends(get_session)
) -> JSONResponse:
    """Create a new database with production and test schemas."""
    uid = create_input.uid
    metric_service = get_otlp_metric_service()
    m = metric_service.get_meter()(func="create_database")
    span_service = get_otlp_span_service()
    span = span_service.get_span()(uid=uid)
    with span.start(
        func_name="create_db", add_source_function_name=True, attributes={"uid": uid}
    ) as span_context:
        database_name = create_input.database_name
        description = create_input.description
        space_id = create_input.space_id
        need_check = {
            "database_name": database_name,
            "uid": uid,
            "description": description,
            "space_id": space_id,
        }
        span_context.add_info_events(need_check)
        span_context.add_info_event(f"database_name: {database_name}")
        span_context.add_info_event(f"uid: {uid}")

        try:
            database_info = await exec_generate_schema(create_input, span_context, db)
            m.in_success_count(lables={"uid": uid})
            return format_response(  # type: ignore[no-any-return]
                CodeEnum.Successes.code,
                message=CodeEnum.Successes.msg,
                data={"database_id": database_info.database_id},
                sid=span_context.sid,
            )
        except sqlalchemy.exc.IntegrityError as e:
            await db.rollback()
            m.in_error_count(
                CodeEnum.DatabaseExecutionError.code,
                lables={"uid": uid},
                span=span_context,
            )
            span_context.record_exception(e)
            return format_response(  # type: ignore[no-any-return]
                CodeEnum.DatabaseExecutionError.code,
                message="Database consistency error, "
                "created database name cannot be duplicated",
                sid=span_context.sid,
            )
        except Exception as e:  # pylint: disable=broad-except
            await db.rollback()
            m.in_error_count(
                CodeEnum.CreatDBError.code, lables={"uid": uid}, span=span_context
            )
            span_context.record_exception(e)
            return format_response(  # type: ignore[no-any-return]
                CodeEnum.CreatDBError.code,
                message=str(e.__cause__),
                sid=span_context.sid,
            )


@drop_db_router.post("/drop_database", response_class=JSONResponse)
async def drop_db(
    drop_input: DropDBInput, db: AsyncSession = Depends(get_session)
) -> JSONResponse:
    """Drop an existing database and all its schemas."""
    database_id = drop_input.database_id
    uid = drop_input.uid
    metric_service = get_otlp_metric_service()
    m = metric_service.get_meter()(func="drop_database")
    span_service = get_otlp_span_service()
    span = span_service.get_span()(uid=uid)
    with span.start(
        func_name="drop_db",
        add_source_function_name=True,
        attributes={"database_id": database_id, "uid": uid},
    ) as span_context:
        space_id = drop_input.space_id
        need_check = {"database_id": database_id, "uid": uid, "space_id": space_id}
        span_context.add_info_events(need_check)
        span_context.add_info_event(f"database_id: {database_id}")
        span_context.add_info_event(f"uid: {uid}")

        if space_id:
            span_context.add_info_event(f"space_id: {space_id}")
            create_uid_res = await get_uid_by_did_space_id(db, database_id, space_id)
            if not create_uid_res:
                m.in_error_count(
                    CodeEnum.SpaceIDNotExistError.code,
                    lables={"space_ud": space_id},
                    span=span_context,
                )
                span_context.add_error_event(f"space_id: {space_id} does not exist")
                return format_response(  # type: ignore[no-any-return]
                    code=CodeEnum.SpaceIDNotExistError.code,
                    message=f"Team space space_id: {space_id} does not exist",
                    sid=span_context.sid,
                )
            uid = create_uid_res[0][0]
            if not isinstance(uid, str):
                uid = str(uid)

        schema_list, error_resp = await check_database_exists_by_did_uid(
            db, database_id, uid, span_context, m
        )
        if error_resp:
            return error_resp  # type: ignore[no-any-return]

        try:
            await del_database_meta_by_did(db, database_id)
            await del_schema_meta_by_did(db, database_id)
        except Exception as e:  # pylint: disable=broad-except
            await db.rollback()
            span_context.report_exception(e)
            m.in_error_count(
                CodeEnum.DeleteDBError.code, lables={"uid": uid}, span=span_context
            )
            return format_response(  # type: ignore[no-any-return]
                code=CodeEnum.DeleteDBError.code,
                message=f"Failed to delete database, {str(e.__cause__)}",
                sid=span_context.sid,
            )

        try:
            for schema in schema_list:
                await db.exec(text(f'DROP SCHEMA IF EXISTS "{schema[0]}" CASCADE;'))  # type: ignore[call-overload]
            await db.commit()
            m.in_success_count(lables={"uid": uid})
            return format_response(  # type: ignore[no-any-return]
                CodeEnum.Successes.code,
                message=CodeEnum.Successes.msg,
                sid=span_context.sid,
            )
        except Exception as e:  # pylint: disable=broad-except
            span_context.record_exception(e)
            await db.rollback()
            m.in_error_count(
                CodeEnum.DeleteDBError.code, lables={"uid": uid}, span=span_context
            )
            return format_response(  # type: ignore[no-any-return]
                code=CodeEnum.DeleteDBError.code,
                message=f"{str(e.__cause__)}",
                sid=span_context.sid,
            )


@modify_db_description_router.post(
    "/modify_db_description", response_class=JSONResponse
)
async def modify_db_description(
    modify_input: ModifyDBDescInput, db: AsyncSession = Depends(get_session)
) -> JSONResponse:
    """Modify the description of an existing database."""
    database_id = modify_input.database_id
    uid = modify_input.uid
    description = modify_input.description
    metric_service = get_otlp_metric_service()
    m = metric_service.get_meter()(func="modify_db_description")
    span_service = get_otlp_span_service()
    span = span_service.get_span()(uid=uid)
    with span.start(
        func_name="modify_db_description",
        add_source_function_name=True,
        attributes={"database_id": database_id, "uid": uid},
    ) as span_context:
        space_id = modify_input.space_id
        need_check = {
            "database_id": database_id,
            "uid": uid,
            "description": description,
            "space_id": space_id,
        }
        span_context.add_info_events(need_check)
        span_context.add_info_event(f"database_id: {database_id}")
        span_context.add_info_event(f"uid: {uid}")

        try:
            if space_id:
                span_context.add_info_event(f"space_id: {space_id}")
                create_uid_res = await get_uid_by_did_space_id(
                    db, database_id, space_id
                )
                if not create_uid_res:
                    m.in_error_count(
                        CodeEnum.SpaceIDNotExistError.code,
                        lables={"space_id": space_id},
                        span=span_context,
                    )
                    span_context.add_error_event(f"space_id: {space_id} does not exist")
                    return format_response(  # type: ignore[no-any-return]
                        code=CodeEnum.SpaceIDNotExistError.code,
                        message=f"Team space space_id: {space_id} does not exist",
                        sid=span_context.sid,
                    )
                uid = create_uid_res[0][0]
                if not isinstance(uid, str):
                    uid = str(uid)

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
                return format_response(  # type: ignore[no-any-return]
                    code=CodeEnum.DatabaseNotExistError.code,
                    message=f"uid: {uid} or database_id: {database_id} error, "
                    "please verify",
                    sid=span_context.sid,
                )
            await update_database_meta_by_did_uid(
                db, database_id=database_id, uid=uid, description=description
            )
            await db.commit()
            m.in_success_count(lables={"uid": uid})
            return format_response(  # type: ignore[no-any-return]
                CodeEnum.Successes.code,
                message=CodeEnum.Successes.msg,
                sid=span_context.sid,
            )
        except Exception as e:  # pylint: disable=broad-except
            span_context.record_exception(e)
            await db.rollback()
            m.in_error_count(
                CodeEnum.ModifyDBDescriptionError.code,
                lables={"uid": uid},
                span=span_context,
            )
            return format_response(  # type: ignore[no-any-return]
                code=CodeEnum.ModifyDBDescriptionError.code,
                message=f"{str(e.__cause__)}",
                sid=span_context.sid,
            )
